import multiprocessing
from threading import Event, Lock, Thread
from time import monotonic

from fastapi import HTTPException

from .encoder import E5Encoder, EmbeddingInputError, validate_vectors
from .settings import EmbeddingSettings


def _embedding_worker(settings, connection):
    encoder = E5Encoder(settings)
    try:
        while True:
            texts, input_type = connection.recv()
            try:
                connection.send((200, encoder.embed(texts, input_type=input_type)))
            except EmbeddingInputError:
                connection.send((422, "Embedding input exceeds the supported token budget."))
            except Exception:
                # No provider errors, paths, source content or credentials cross the boundary.
                connection.send((503, "Embedding model is unavailable or misconfigured."))
    except (EOFError, OSError):
        pass
    finally:
        connection.close()


class EmbeddingService:
    """Reuse one CPU model, with a process boundary for cancellable loading/inference."""

    def __init__(self, settings: EmbeddingSettings):
        self.settings = settings
        self._lock = Lock()
        self._worker = None
        self._connection = None

    def _stop(self):
        connection, worker = self._connection, self._worker
        self._connection = self._worker = None
        try:
            # start() can fail before a process has a PID; joining it would poison recovery.
            if worker is not None and worker.pid is not None:
                if worker.is_alive():
                    worker.terminate()
                worker.join(timeout=2)
                if worker.is_alive():
                    worker.kill()
                    worker.join(timeout=2)
                worker.close()
        finally:
            if connection is not None:
                connection.close()

    def close(self):
        with self._lock:
            self._stop()

    def embed(self, texts: list[str], input_type: str = "passage") -> dict:
        if input_type not in ("passage", "query"):
            raise HTTPException(422, "Invalid embedding input type.")
        if not self._lock.acquire(blocking=False):
            raise HTTPException(503, "Embedding is busy; retry later.")
        deadline = monotonic() + self.settings.timeout_seconds
        exchange_thread = None
        try:
            if self._worker is None or not self._worker.is_alive():
                self._stop()
                context = multiprocessing.get_context("spawn")
                self._connection, child = context.Pipe()
                self._worker = context.Process(target=_embedding_worker, args=(self.settings, child), daemon=True)
                try:
                    self._worker.start()
                finally:
                    child.close()
            connection = self._connection
            completed = Event()
            outcome = []

            def exchange():
                try:
                    # send/recv may block on a full pipe or partial response, so both
                    # run under the same deadline enforced by the request thread.
                    connection.send((texts, input_type))
                    outcome.append(connection.recv())
                except (EOFError, OSError, ValueError):
                    pass
                finally:
                    completed.set()

            exchange_thread = Thread(target=exchange, daemon=True)
            exchange_thread.start()
            if not completed.wait(max(0., deadline - monotonic())) or monotonic() > deadline:
                raise HTTPException(504, "Embedding timed out.")
            if not outcome:
                raise HTTPException(503, "Embedding worker is unavailable.")
            status, result = outcome[0]
            if status != 200:
                raise HTTPException(status, result)
            validate_vectors(result, len(texts), self.settings.dimensions)
            return {"model": self.settings.identity, "dimensions": self.settings.dimensions, "vectors": result}
        except HTTPException:
            self._stop()
            raise
        except (EOFError, OSError, ValueError, RuntimeError):
            self._stop()
            raise HTTPException(503, "Embedding worker is unavailable.") from None
        finally:
            if exchange_thread is not None:
                exchange_thread.join(timeout=0.2)
            self._lock.release()

"""Spawn targets without importing the HTTP app in each synthetic worker process."""
import time


def echo_worker(settings, connection):
    try:
        while True:
            texts, input_type = connection.recv()
            if texts == ["hang"]:
                time.sleep(10)
            connection.send((200, [[1.] + [0.] * (settings.dimensions - 1) for _ in texts]))
    except EOFError:
        pass
    finally:
        connection.close()


def delayed_reader(settings, connection):
    try:
        time.sleep(5)
        texts, input_type = connection.recv()
        connection.send((200, [[1.] + [0.] * (settings.dimensions - 1) for _ in texts]))
    finally:
        connection.close()

import { formatSourcePages } from './aiDisplay.js'

export function CitationList({ citations, courseId, onOpenSource }) {
  return <section className="citation-list" aria-label="Nguồn trích dẫn"><h4>Nguồn trích dẫn</h4>
    <ol>{citations.map((item, index) => <li key={`${item.chunkId}-${index}`}>
      <strong>{item.documentTitle}</strong><span className="inline-help"> · {formatSourcePages(item.pageStart, item.pageEnd)}</span>
      <blockquote className="ai-text">{item.quote}</blockquote>
      <button className="secondary-button" type="button" onClick={() => onOpenSource({ courseId, documentId: item.documentId, chunkId: item.chunkId, quote: item.quote })}>Xem nguồn</button>
    </li>)}</ol>
  </section>
}

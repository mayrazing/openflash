/**
 * 把含换行符的文本按行渲染，行间插入 <br>，不用 dangerouslySetInnerHTML
 * props:
 *   text      - 待渲染的字符串，可含 \n
 *   className - 透传给外层 span 的 class
 */
export default function TextWithBreaks({ text, className }) {
    if (!text) return null
    const lines = text.split('\n')
    return (
        <span className={className}>
            {lines.map((line, i) => (
                <span key={i}>
                    {line}
                    {i < lines.length - 1 && <br />}
                </span>
            ))}
        </span>
    )
}
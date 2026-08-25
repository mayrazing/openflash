import TextWithBreaks from './TextWithBreaks'
import { splitFaceContent } from '../lib/richFaceOrder'

export default function RichFaceContent({ text, images, className, textClassName, imageClassName }) {
  const segments = splitFaceContent(text, images)
  if (segments.length === 0) return null

  return (
    <span className={className}>
      {segments.map((segment, index) => (
        segment.type === 'image'
          ? <img key={`image-${segment.index}-${index}`} src={segment.src} alt="" className={imageClassName} />
          : <TextWithBreaks key={`text-${index}`} text={segment.text} className={textClassName} />
      ))}
    </span>
  )
}

/**
 * 把 base64 图片上传到服务器，返回可访问的相对路径
 *
 * @param {string} base64  含 data:image/jpeg;base64, 前缀的 base64 字符串
 * @returns {Promise<string>}  服务器返回的图片路径，如 /uploads/xxx.jpg
 */
export async function uploadImage(base64) {
    const res = await fetch(base64)
    const blob = await res.blob()
    const formData = new FormData()
    formData.append('file', blob, 'image.jpg')
    const response = await fetch('/api/upload', { method: 'POST', body: formData })
    if (!response.ok) throw new Error('图片上传失败')
    const json = await response.json()
    return json.data.url
}

/**
 * 把用户选择的图片文件压缩后转为 base64 字符串
 * 压缩规则：长边不超过 1200px，质量 80%
 *
 * @param {File} file  用户选择的图片文件对象
 * @returns {Promise<string>}  压缩后的 base64 字符串（含 data:image/jpeg;base64, 前缀）
 */
export function compressImage(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onerror = () => reject(new Error('文件读取失败'))
        reader.onload = (e) => {
            const img = new Image()
            img.onerror = () => reject(new Error('图片加载失败'))
            img.onload = () => {
                const MAX = 1200
                let { width, height } = img

                // 等比缩放：长边超过 1200px 时缩小
                if (width > MAX || height > MAX) {
                    if (width >= height) {
                        height = Math.round((height / width) * MAX)
                        width = MAX
                    } else {
                        width = Math.round((width / height) * MAX)
                        height = MAX
                    }
                }

                const canvas = document.createElement('canvas')
                canvas.width = width
                canvas.height = height
                const ctx = canvas.getContext('2d')
                ctx.drawImage(img, 0, 0, width, height)
                resolve(canvas.toDataURL('image/jpeg', 0.8))
            }
            img.src = e.target.result
        }
        reader.readAsDataURL(file)
    })
}
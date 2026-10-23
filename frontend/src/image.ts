/**
 * Prepares a photo for upload: large camera photos are scaled to at most 2000 px on the long side
 * and re-encoded as JPEG, so a 6 MB phone picture becomes a few hundred KB. Other files pass unchanged.
 */
const MAX_SIDE = 2000;
const RESIZE_ABOVE_BYTES = 1_200_000;

export async function prepareUpload(file: File): Promise<{ blob: Blob; name: string }> {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/webp';
  if (!isImage || typeof createImageBitmap !== 'function') return { blob: file, name: file.name };
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size <= RESIZE_ABOVE_BYTES) { bitmap.close(); return { blob: file, name: file.name }; }
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', 0.85));
    if (!blob) return { blob: file, name: file.name };
    return { blob, name: file.name.replace(/\.[^.]+$/, '') + '.jpg' };
  } catch {
    return { blob: file, name: file.name };
  }
}

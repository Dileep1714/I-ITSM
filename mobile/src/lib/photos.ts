import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';
import { Alert } from 'react-native';

export interface PickedPhoto {
  uri: string;
  name: string;
  type: string;
}

/** Take a photo (e.g. of an error on another screen) or choose one from the gallery. Null if cancelled. */
export async function pickPhoto(source: 'camera' | 'library'): Promise<PickedPhoto | null> {
  try {
    if (source === 'camera') {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        Alert.alert('Camera access needed', 'Allow camera access in Settings to photograph the problem.');
        return null;
      }
    }
    const options: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 0.6, exif: false };
    const result = source === 'camera' ? await ImagePicker.launchCameraAsync(options) : await ImagePicker.launchImageLibraryAsync(options);
    if (result.canceled || !result.assets[0]) return null;
    const asset = result.assets[0];
    return await shrink(asset);
  } catch {
    Alert.alert('Photo not available', 'The camera or gallery could not be opened on this device.');
    return null;
  }
}

/** Longest side of an uploaded photo: plenty to read an error message, and a few hundred KB instead of megabytes. */
const MAX_SIDE = 1600;

/**
 * Phone cameras produce 3-10 MB photos, above the server's 5 MB limit and slow on mobile data. Every photo is
 * re-encoded as a JPEG at most 1600 px on its longest side before upload. If that fails, the original is sent and
 * the server reports a clear "too large" error if it is.
 */
async function shrink(asset: ImagePicker.ImagePickerAsset): Promise<PickedPhoto> {
  const name = `photo-${Date.now()}.jpg`;
  try {
    const context = ImageManipulator.manipulate(asset.uri);
    if (Math.max(asset.width, asset.height) > MAX_SIDE) {
      context.resize(asset.width >= asset.height ? { width: MAX_SIDE } : { height: MAX_SIDE });
    }
    const image = await context.renderAsync();
    const saved = await image.saveAsync({ compress: 0.7, format: SaveFormat.JPEG });
    return { uri: saved.uri, type: 'image/jpeg', name };
  } catch {
    const type = asset.mimeType ?? 'image/jpeg';
    return { uri: asset.uri, type, name: asset.fileName ?? name };
  }
}

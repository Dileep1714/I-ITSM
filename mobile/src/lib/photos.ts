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
    const type = asset.mimeType ?? 'image/jpeg';
    return { uri: asset.uri, type, name: asset.fileName ?? `photo-${Date.now()}.${type.split('/')[1] ?? 'jpg'}` };
  } catch {
    Alert.alert('Photo not available', 'The camera or gallery could not be opened on this device.');
    return null;
  }
}

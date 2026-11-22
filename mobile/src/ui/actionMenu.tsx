import Ionicons from '@expo/vector-icons/Ionicons';
import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { ActionSheetIOS, Platform, Pressable, Text, View } from 'react-native';
import type { IconName } from './kit';
import { Sheet } from './kit';
import { useColors } from './theme';

export interface MenuItem {
  label: string;
  icon?: IconName;
  destructive?: boolean;
  onPress: () => void;
}

const MenuContext = createContext<(title: string, items: MenuItem[]) => void>(() => {});

/**
 * "More actions" menu in each platform's own idiom: the system action sheet on iOS, a Material bottom sheet
 * list on Android.
 */
export function ActionMenuProvider({ children }: { children: ReactNode }) {
  const c = useColors();
  const [menu, setMenu] = useState<{ title: string; items: MenuItem[] } | null>(null);

  const show = useCallback((title: string, items: MenuItem[]) => {
    if (Platform.OS === 'ios') {
      const destructive = items.map((i, n) => (i.destructive ? n : -1)).filter((n) => n >= 0);
      ActionSheetIOS.showActionSheetWithOptions(
        { title, options: [...items.map((i) => i.label), 'Cancel'], cancelButtonIndex: items.length, destructiveButtonIndex: destructive },
        (index) => items[index]?.onPress(),
      );
    } else {
      setMenu({ title, items });
    }
  }, []);

  return (
    <MenuContext.Provider value={show}>
      {children}
      <Sheet visible={menu !== null} title={menu?.title} onClose={() => setMenu(null)}>
        <View style={{ marginHorizontal: -20, marginTop: -8 }}>
          {menu?.items.map((item) => (
            <Pressable
              key={item.label}
              android_ripple={{ color: c.divider }}
              onPress={() => {
                setMenu(null);
                item.onPress();
              }}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 16, paddingHorizontal: 24, height: 56 }}
            >
              {item.icon && <Ionicons name={item.icon} size={22} color={item.destructive ? c.danger : c.muted} />}
              <Text style={{ fontSize: 16, color: item.destructive ? c.danger : c.text }}>{item.label}</Text>
            </Pressable>
          ))}
        </View>
      </Sheet>
    </MenuContext.Provider>
  );
}

export const useActionMenu = () => useContext(MenuContext);

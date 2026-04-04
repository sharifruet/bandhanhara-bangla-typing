import { useRef, useState } from 'react';
import { Text, Pressable, StyleSheet, View, Animated } from 'react-native';
import * as Haptics from 'expo-haptics';

interface KeyProps {
  label: string;
  longPressLabel?: string;
  onTap: (char: string) => void;
  flex?: number;
  action?: boolean;
  keyHeight: number;
  fontSize: number;
}

const IOS_KEY_BG = '#FFFFFF';
const IOS_ACTION_BG = '#AEB6BF';
const IOS_PRESSED_BG = '#c8cdd2';

export function Key({ label, longPressLabel, onTap, flex = 1, action, keyHeight, fontSize }: KeyProps) {
  const [pressed, setPressed] = useState(false);
  const longPressedRef = useRef(false);

  const isBengali = /[\u0980-\u09FF।,]/.test(label);
  const isAction = action || !isBengali;

  const handlePress = () => {
    if (longPressedRef.current) {
      longPressedRef.current = false;
      return;
    }
    onTap(label);
  };

  const handleLongPress = () => {
    if (!longPressLabel) return;
    longPressedRef.current = true;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    onTap(longPressLabel);
  };

  const popupSize = keyHeight * 1.6;
  const popupFontSize = Math.round(fontSize * 1.5);

  return (
    <Pressable
      style={{ flex, padding: 3, paddingBottom: 6 }}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      onPress={handlePress}
      onLongPress={handleLongPress}
      delayLongPress={320}
    >
      {/* iOS-style popup above the key */}
      {pressed && !isAction && (
        <View style={[styles.popup, { width: popupSize, height: popupSize, bottom: keyHeight + 8 }]}>
          <Text style={[styles.popupChar, { fontSize: popupFontSize }]}>{label}</Text>
        </View>
      )}

      <View
        style={[
          styles.key,
          { height: keyHeight, backgroundColor: pressed ? IOS_PRESSED_BG : isAction ? IOS_ACTION_BG : IOS_KEY_BG },
          !pressed && styles.shadow,
        ]}
      >
        <Text style={[styles.primary, { fontSize, color: '#111827' }]}>{label}</Text>
        {longPressLabel && (
          <Text style={[styles.hint, { fontSize: Math.max(9, fontSize * 0.38) }]}>
            {longPressLabel}
          </Text>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  key: {
    borderRadius: 5,
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  shadow: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.4,
    shadowRadius: 0,
    elevation: 2,
  },
  primary: {
    fontFamily: 'NotoSansBengali',
    lineHeight: undefined,
  },
  hint: {
    color: '#6b7280',
    position: 'absolute',
    top: 3,
    right: 4,
    fontFamily: 'NotoSansBengali',
  },
  popup: {
    position: 'absolute',
    alignSelf: 'center',
    backgroundColor: '#fff',
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.22,
    shadowRadius: 8,
    elevation: 8,
    zIndex: 100,
  },
  popupChar: {
    fontFamily: 'NotoSansBengali',
    color: '#111827',
    lineHeight: undefined,
  },
});

import { View, StyleSheet, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { LAYER1_KEYS, LAYER2_KEYS, LAYER3_KEYS } from '../data/keys';
import { useLayerState } from '../hooks/useLayerState';
import { Key } from './Key';

interface BanglaKeyboardProps {
  onChar: (char: string) => void;
  onBackspace: () => void;
  onEnter: () => void;
}

const COLS = 6;
const H_PADDING = 6; // horizontal keyboard padding (each side)
const KEY_GAP = 5;   // margin on each side of a key

export function BanglaKeyboard({ onChar, onBackspace, onEnter }: BanglaKeyboardProps) {
  const { layer, mode, onShiftTap, onL3Tap, onCharInserted } = useLayerState();
  const { width: screenWidth } = useWindowDimensions();
  const insets = useSafeAreaInsets();

  // Compute key dimensions from screen width so keys are as large as possible
  const keyWidth = (screenWidth - H_PADDING * 2 - KEY_GAP * 2 * COLS) / COLS;
  const keyHeight = Math.round(keyWidth * 0.95); // slightly shorter than wide
  const fontSize = Math.round(keyHeight * 0.48);

  const handleChar = (char: string) => {
    onChar(char);
    onCharInserted();
  };

  const activeKeys = layer === 1 ? LAYER1_KEYS : layer === 2 ? LAYER2_KEYS : LAYER3_KEYS;

  const rows: typeof activeKeys[] = [];
  for (let i = 0; i < activeKeys.length; i += COLS) {
    rows.push(activeKeys.slice(i, i + COLS));
  }

  const shiftLabel =
    layer === 2 && mode === 'locked' ? '⇪' : layer !== 1 ? '⇧' : mode === 'oneshot' ? '⇧¹' : '⇧';

  const keyProps = { keyHeight, fontSize };

  return (
    <View style={[styles.keyboard, { paddingBottom: insets.bottom + 6 }]}>
      {/* Character rows */}
      {rows.map((row, ri) => (
        <View key={ri} style={styles.row}>
          {row.map((key, ki) => (
            <Key
              key={ki}
              label={key.primary}
              longPressLabel={key.longPress}
              onTap={handleChar}
              {...keyProps}
            />
          ))}
        </View>
      ))}

      {/* Control row — iOS style: ⇧ | … | । | space | ⌫ | ↵ */}
      <View style={styles.row}>
        <Key label={shiftLabel} onTap={onShiftTap} action {...keyProps} />
        <Key label={layer === 3 ? '←' : '…'} onTap={onL3Tap} action {...keyProps} />
        <Key label="।" longPressLabel="," onTap={handleChar} {...keyProps} />
        <Key label=" " onTap={handleChar} flex={2} action {...keyProps} />
        <Key label="⌫" onTap={onBackspace} action {...keyProps} />
        <Key label="↵" onTap={onEnter} action {...keyProps} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  keyboard: {
    backgroundColor: '#CDD0D6',
    paddingHorizontal: H_PADDING,
    paddingTop: 8,
    borderTopWidth: 0,
  },
  row: {
    flexDirection: 'row',
    marginBottom: KEY_GAP,
  },
});

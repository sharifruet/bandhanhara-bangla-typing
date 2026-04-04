import { LAYER1_KEYS, LAYER2_KEYS, LAYER3_KEYS } from '../data/keys';
import { useLayerState } from '../hooks/useLayerState';
import { Key } from './Key';
import styles from './BanglaKeyboard.module.css';

interface BanglaKeyboardProps {
  onChar: (char: string) => void;
  onBackspace: () => void;
  onEnter: () => void;
}

export function BanglaKeyboard({ onChar, onBackspace, onEnter }: BanglaKeyboardProps) {
  const { layer, mode, onShiftTap, onL3Tap, onCharInserted } = useLayerState();

  const handleChar = (char: string) => {
    onChar(char);
    onCharInserted();
  };

  const activeKeys = layer === 1 ? LAYER1_KEYS : layer === 2 ? LAYER2_KEYS : LAYER3_KEYS;

  const shiftLabel = layer === 2 && mode === 'locked' ? '⇪' : '⇧';

  return (
    <div className={styles.keyboard}>
      {/* Character grid — 6 per row */}
      <div className={styles.grid}>
        {activeKeys.map((key, i) => (
          <Key
            key={i}
            label={key.primary}
            longPressLabel={key.longPress}
            onTap={handleChar}
          />
        ))}
      </div>

      {/* Control row — iOS style: shift | । | space(wide) | ⌫ | ↵ */}
      <div className={styles.controlRow}>
        <Key label={shiftLabel} onTap={onShiftTap} action />
        <Key label={layer === 3 ? '←' : '…'} onTap={onL3Tap} action />
        <Key label="।" longPressLabel="," onTap={handleChar} />
        <Key label=" " onTap={handleChar} wide action />
        <Key label="⌫" onTap={() => onBackspace()} action />
        <Key label="↵" onTap={() => onEnter()} action />
      </div>
    </div>
  );
}

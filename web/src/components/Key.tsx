import { useState, useRef } from 'react';
import styles from './Key.module.css';

interface KeyProps {
  label: string;
  longPressLabel?: string;
  onTap: (char: string) => void;
  wide?: boolean;
  action?: boolean;
}

const LONG_PRESS_MS = 320;

export function Key({ label, longPressLabel, onTap, wide, action }: KeyProps) {
  const [pressed, setPressed] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const firedRef = useRef(false);

  const startPress = () => {
    firedRef.current = false;
    setPressed(true);
    if (longPressLabel) {
      timerRef.current = setTimeout(() => {
        firedRef.current = true;
        setPressed(false);
        onTap(longPressLabel);
      }, LONG_PRESS_MS);
    }
  };

  const endPress = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setPressed(false);
    if (!firedRef.current) onTap(label);
  };

  const cancelPress = () => {
    if (timerRef.current) clearTimeout(timerRef.current);
    setPressed(false);
  };

  const isAction = action || (!label.match(/[\u0980-\u09FF।,]/));

  return (
    <div
      className={`${styles.keyWrap} ${wide ? styles.wide : ''}`}
      onPointerDown={startPress}
      onPointerUp={endPress}
      onPointerLeave={cancelPress}
    >
      {/* Popup — shown while pressed, only for character keys */}
      {pressed && !isAction && (
        <div className={styles.popup}>
          <span className={styles.popupChar}>{label}</span>
        </div>
      )}

      <div className={`${styles.key} ${isAction ? styles.action : ''} ${pressed ? styles.pressed : ''}`}>
        <span className={styles.primary}>{label}</span>
        {longPressLabel && <span className={styles.hint}>{longPressLabel}</span>}
      </div>
    </div>
  );
}

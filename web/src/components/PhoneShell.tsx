import { useEffect, useState, type ReactNode } from 'react';
import styles from './PhoneShell.module.css';

// iPhone 16 Pro Max logical resolution
const PHONE_W = 430;
const PHONE_H = 932;

interface PhoneShellProps {
  children: ReactNode;
}

export function PhoneShell({ children }: PhoneShellProps) {
  const [scale, setScale] = useState(1);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const update = () => {
      const mobile = window.innerWidth <= 500;
      setIsMobile(mobile);
      if (!mobile) {
        const s = Math.min(
          (window.innerWidth * 0.95) / PHONE_W,
          (window.innerHeight * 0.95) / PHONE_H,
        );
        setScale(Math.min(s, 1)); // never scale up beyond 1:1
      }
    };

    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  if (isMobile) {
    // On a real phone: full screen, no shell
    return <div className={styles.fullscreen}>{children}</div>;
  }

  return (
    <div className={styles.stage}>
      <div
        className={styles.shell}
        style={{
          width: PHONE_W,
          height: PHONE_H,
          transform: `scale(${scale})`,
          transformOrigin: 'center center',
        }}
      >
        <div className={styles.dynamicIsland} />
        <div className={styles.screen}>{children}</div>
      </div>
    </div>
  );
}

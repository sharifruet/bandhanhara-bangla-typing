import { useState } from 'react';
import type { Layer } from '../data/keys';

export type LayerMode = 'normal' | 'oneshot' | 'locked';

interface LayerState {
  layer: Layer;
  mode: LayerMode;
}

interface UseLayerState {
  layer: Layer;
  mode: LayerMode;
  // Call when the L2 switch key is tapped
  onShiftTap: () => void;
  // Call when the L3 switch key is tapped
  onL3Tap: () => void;
  // Call after a character key is inserted (handles one-shot return)
  onCharInserted: () => void;
}

export function useLayerState(): UseLayerState {
  const [state, setState] = useState<LayerState>({ layer: 1, mode: 'normal' });

  const onShiftTap = () => {
    setState(prev => {
      if (prev.layer === 1) {
        // First tap → one-shot L2
        if (prev.mode === 'normal') return { layer: 2, mode: 'oneshot' };
        // Second tap while one-shot → lock L2
        if (prev.mode === 'oneshot') return { layer: 2, mode: 'locked' };
      }
      // Tap shift while on L2 locked → back to L1
      return { layer: 1, mode: 'normal' };
    });
  };

  const onL3Tap = () => {
    setState(prev =>
      prev.layer === 3
        ? { layer: 1, mode: 'normal' }
        : { layer: 3, mode: 'locked' }
    );
  };

  const onCharInserted = () => {
    setState(prev => {
      if (prev.mode === 'oneshot') return { layer: 1, mode: 'normal' };
      return prev;
    });
  };

  return { layer: state.layer, mode: state.mode, onShiftTap, onL3Tap, onCharInserted };
}

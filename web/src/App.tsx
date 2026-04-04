import { useState } from 'react';
import { BanglaKeyboard } from './components/BanglaKeyboard';
import { PhoneShell } from './components/PhoneShell';
import './App.css';

function App() {
  const [text, setText] = useState('');

  const handleChar = (char: string) => setText(t => t + char);
  const handleBackspace = () => setText(t => [...t].slice(0, -1).join(''));
  const handleEnter = () => setText(t => t + '\n');
  const handleCopy = () => navigator.clipboard.writeText(text);

  return (
    <PhoneShell>
      <div className="app">
        <div className="output-area">
          <div className="output-text">
            {text || <span className="placeholder">Start typing…</span>}
          </div>
          <button className="copy-btn" onClick={handleCopy} disabled={!text}>
            Copy
          </button>
        </div>
        <BanglaKeyboard
          onChar={handleChar}
          onBackspace={handleBackspace}
          onEnter={handleEnter}
        />
      </div>
    </PhoneShell>
  );
}

export default App;

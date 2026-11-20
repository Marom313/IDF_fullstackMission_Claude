// Fonts are bundled locally (OFL) – no CDN, works in a closed network.
import '@fontsource/heebo/400.css';
import '@fontsource/heebo/700.css';
import '@fontsource/noto-sans-arabic/400.css';
import '@fontsource/noto-sans-arabic/700.css';
import '@fontsource/noto-sans/cyrillic-400.css';
import '@fontsource/noto-sans/cyrillic-700.css';
import './shared/i18n/i18n';
import './shared/styles/global.scss';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

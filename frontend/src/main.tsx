import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { installMocks, usingMocks } from './api';
import { initAuth } from './auth';
// Fonts are self-hosted (no requests to Google: GDPR) and cover Latin Extended + Cyrillic.
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-sans-condensed/500.css';
import '@fontsource/ibm-plex-sans-condensed/600.css';
import './styles.css';

const root = createRoot(document.getElementById('root')!);

(usingMocks ? installMocks() : Promise.resolve())
  .then(() => initAuth())
  .then(() => root.render(<StrictMode><App /></StrictMode>))
  .catch((e) => {
    root.render(<p style={{ padding: 24 }}>Sign-in service is unreachable: {String(e)}</p>);
  });

import React from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import App from './App';
import PageReloadGate from './components/common/PageReloadGate';
import './assets/styles/global.css';

// Browser refresh starts a fresh job search before React reads the URL.
if (
  /^\/jobs\/?$/.test(window.location.pathname) &&
  performance.getEntriesByType('navigation')[0]?.type === 'reload'
) {
  window.history.replaceState(window.history.state, '', window.location.pathname);
}

const router = createBrowserRouter([{ path: '*', element: <App /> }]);

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <PageReloadGate><RouterProvider router={router} /></PageReloadGate>
  </React.StrictMode>,
);

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { GoogleOAuthProvider } from '@react-oauth/google'
import App from './App.jsx'
import './index.css'
import '@/shared/styles/global.css'

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID

if (!googleClientId) {
  console.warn('VITE_GOOGLE_CLIENT_ID is not set. Google login will not work.')
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <GoogleOAuthProvider clientId={googleClientId ?? ''}>
      <App />
    </GoogleOAuthProvider>
  </StrictMode>,
)
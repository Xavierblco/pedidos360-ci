import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Authenticator } from "@aws-amplify/ui-react";
import "@aws-amplify/ui-react/styles.css";
import "./amplifyConfig";

import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Productos from "./pages/Productos";
import Pedidos from "./pages/Pedidos";
import Mensajeria from "./pages/Mensajeria";
import ProtectedRoute from "./router/ProtectedRoute";

export default function App() {
  return (
    // Authenticator.Provider da contexto de sesión (useAuthenticator) a toda la app,
    // sin forzar el formulario de login fuera de /login.
    <Authenticator.Provider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
          <Route path="/productos" element={<ProtectedRoute><Productos /></ProtectedRoute>} />
          <Route path="/pedidos" element={<ProtectedRoute><Pedidos /></ProtectedRoute>} />
          <Route path="/mensajeria" element={<ProtectedRoute><Mensajeria /></ProtectedRoute>} />
        </Routes>
      </BrowserRouter>
    </Authenticator.Provider>
  );
}

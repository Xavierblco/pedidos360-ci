import { fetchAuthSession } from "aws-amplify/auth";

const API_URL = import.meta.env.VITE_API_URL;
// Parcial 2: mensajeria-admin. Si no se define, se usa el mismo punto de entrada (API Gateway / Nginx).
const ADMIN_API_URL = import.meta.env.VITE_ADMIN_API_URL || API_URL;

/**
 * Helper centralizado para llamar al backend:
 * 1) obtiene el token de sesión de Cognito (Amplify),
 * 2) agrega el header Authorization,
 * 3) hace la petición,
 * 4) maneja errores comunes (401/403) de forma consistente,
 * 5) devuelve la respuesta ya parseada.
 */
export async function apiFetch(path, options = {}) {
  return request(API_URL, path, options);
}

/** Igual que apiFetch, pero contra el microservicio mensajeria-admin (requiere rol ADMIN). */
export async function adminFetch(path, options = {}) {
  return request(ADMIN_API_URL, path, options);
}

async function request(baseUrl, path, options) {
  const session = await fetchAuthSession();
  const token = session.tokens?.idToken?.toString();

  if (!token) {
    throw new Error("No hay sesión activa. Inicia sesión nuevamente.");
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });

  if (response.status === 401) {
    throw new Error("401 Unauthorized: token ausente, inválido o expirado");
  }
  if (response.status === 403) {
    throw new Error("403 Forbidden: no tienes permisos para esta operación");
  }
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Error ${response.status}: ${body}`);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

/** Lee los claims del usuario (email, roles/grupos) directamente del ID Token. */
export async function getUsuarioActual() {
  const session = await fetchAuthSession();
  const payload = session.tokens?.idToken?.payload;
  if (!payload) return null;
  return {
    email: payload["email"],
    grupos: payload["cognito:groups"] || [],
  };
}

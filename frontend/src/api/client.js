const API_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

export function backendUrl(path = '') {
  return `${API_URL}${path}`;
}

export async function request(path, options = {}) {
  const token = sessionStorage.getItem('relay_token');
  const response = await fetch(backendUrl(path), {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : null;

  if (!response.ok) {
    throw new Error(body?.message || body?.error || 'Request failed');
  }

  return body;
}

export const authApi = {
  register: (payload) =>
    request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  login: (payload) =>
    request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};

export const chatApi = {
  me: () => request('/api/users/me'),
  users: (query = '') => request(`/api/users?q=${encodeURIComponent(query)}`),
  rooms: () => request('/api/rooms'),
  joinRoom: (roomId) =>
    request('/api/rooms/join', {
      method: 'POST',
      body: JSON.stringify({ roomId }),
    }),
  createRoom: (payload) =>
    request('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  messages: (roomId) => request(`/api/messages/${roomId}`),
};

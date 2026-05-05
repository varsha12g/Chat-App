import { createContext, useContext, useMemo, useState } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => sessionStorage.getItem('relay_token'));
  const [user, setUser] = useState(() => {
    const saved = sessionStorage.getItem('relay_user');
    return saved ? JSON.parse(saved) : null;
  });

  function saveSession(authResponse) {
    sessionStorage.setItem('relay_token', authResponse.token);
    sessionStorage.setItem('relay_user', JSON.stringify(authResponse.user));
    localStorage.removeItem('relay_token');
    localStorage.removeItem('relay_user');
    setToken(authResponse.token);
    setUser(authResponse.user);
  }

  function logout() {
    sessionStorage.removeItem('relay_token');
    sessionStorage.removeItem('relay_user');
    localStorage.removeItem('relay_token');
    localStorage.removeItem('relay_user');
    setToken(null);
    setUser(null);
  }

  const value = useMemo(
    () => ({ token, user, saveSession, logout }),
    [token, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}

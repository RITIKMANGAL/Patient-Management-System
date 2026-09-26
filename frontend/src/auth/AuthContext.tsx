import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { configureApiClient } from "../api/apiClient";
import * as authApi from "../api/authApi";
import { isAccessTokenCurrent, userFromAccessToken } from "./jwt";
import { clearStoredTokens, loadStoredTokens, saveStoredTokens } from "./tokenStorage";
import type { ReactNode } from "react";
import type { AuthUser, LoginRequest, RegisterRequest, RoleName, UserResponse } from "../types/auth";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
}

interface AuthContextValue extends AuthState {
  isAuthenticated: boolean;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<UserResponse>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<string | null>;
  hasAnyRole: (roles: RoleName[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading", user: null });
  const accessTokenRef = useRef<string | null>(null);
  const refreshTokenRef = useRef<string | null>(null);
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    refreshTokenRef.current = null;
    clearStoredTokens();
    setState({ status: "unauthenticated", user: null });
  }, []);

  const applyTokens = useCallback((tokens: Awaited<ReturnType<typeof authApi.login>>): string | null => {
    const user = userFromAccessToken(tokens.accessToken);
    if (!user) {
      clearSession();
      return null;
    }

    accessTokenRef.current = tokens.accessToken;
    refreshTokenRef.current = tokens.refreshToken;
    saveStoredTokens(tokens);
    setState({ status: "authenticated", user });
    return tokens.accessToken;
  }, [clearSession]);

  const refreshSession = useCallback(async (): Promise<string | null> => {
    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current;
    }

    const currentRefreshToken = refreshTokenRef.current;
    if (!currentRefreshToken) {
      clearSession();
      return null;
    }

    refreshPromiseRef.current = authApi.refresh(currentRefreshToken)
      .then(applyTokens)
      .catch(() => {
        clearSession();
        return null;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });

    return refreshPromiseRef.current;
  }, [applyTokens, clearSession]);

  useEffect(() => {
    configureApiClient({
      getAccessToken: () => accessTokenRef.current,
      refreshSession,
      clearSession
    });
  }, [clearSession, refreshSession]);

  useEffect(() => {
    const tokens = loadStoredTokens();
    if (!tokens) {
      setState({ status: "unauthenticated", user: null });
      return;
    }

    accessTokenRef.current = tokens.accessToken;
    refreshTokenRef.current = tokens.refreshToken;

    if (isAccessTokenCurrent(tokens.accessToken)) {
      const user = userFromAccessToken(tokens.accessToken);
      setState(user ? { status: "authenticated", user } : { status: "unauthenticated", user: null });
      return;
    }

    void refreshSession();
  }, [refreshSession]);

  const login = useCallback(async (request: LoginRequest) => {
    const tokens = await authApi.login(request);
    applyTokens(tokens);
  }, [applyTokens]);

  const register = useCallback((request: RegisterRequest) => {
    return authApi.register(request);
  }, []);

  const logout = useCallback(async () => {
    try {
      if (refreshTokenRef.current && !isAccessTokenCurrent(accessTokenRef.current ?? "")) {
        await refreshSession();
      }

      const refreshToken = refreshTokenRef.current;
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } catch {
      // Local session cleanup still happens when server-side revocation cannot be completed.
    } finally {
      clearSession();
    }
  }, [clearSession, refreshSession]);

  const hasAnyRole = useCallback((roles: RoleName[]) => {
    if (!state.user) {
      return false;
    }
    return roles.some((role) => state.user?.roles.includes(role));
  }, [state.user]);

  const value = useMemo<AuthContextValue>(() => ({
    ...state,
    isAuthenticated: state.status === "authenticated",
    login,
    register,
    logout,
    refreshSession,
    hasAnyRole
  }), [hasAnyRole, login, logout, refreshSession, register, state]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return value;
}

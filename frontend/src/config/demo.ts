export interface DemoConfiguration {
  enabled: boolean;
  username: string;
  password: string;
}

export function getDemoConfiguration(env: Record<string, string | undefined> = import.meta.env): DemoConfiguration {
  return {
    enabled: env.VITE_DEMO_MODE === "true",
    username: env.VITE_DEMO_USERNAME?.trim() ?? "",
    password: env.VITE_DEMO_PASSWORD ?? ""
  };
}

export const demoConfiguration = getDemoConfiguration();

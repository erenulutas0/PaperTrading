import type { NextConfig } from "next";

const backendBaseUrl =
  (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendBaseUrl}/api/:path*`,
      },
      {
        source: "/ws/:path*",
        destination: `${backendBaseUrl}/ws/:path*`,
      },
    ];
  },
};

export default nextConfig;

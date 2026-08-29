import type { NextConfig } from "next";

/**
 * Photographs uploaded by sellers are served by the Spring API, not by this app,
 * so the image optimiser has to be told that host is allowed. Without it a
 * listing's own photograph 400s while the generated covers — which are local
 * files — render fine, so the failure looks like "only some images are broken".
 */
const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: apiUrl.startsWith("https") ? "https" : "http",
        hostname: new URL(apiUrl).hostname,
        port: new URL(apiUrl).port || undefined,
        pathname: "/api/images/**",
      },
    ],
  },
};

export default nextConfig;

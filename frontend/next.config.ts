import type { NextConfig } from "next";

// dev 환경에서 /api/*를 Spring Boot 백엔드(:8080)로 포워딩하여 동일 origin 가정 유지.
// prod에서는 reverse proxy(Nginx 등)가 동일 역할 수행 — `BACKEND_URL` env로 override 가능.
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

const nextConfig: NextConfig = {
  // dev 서버에서 127.0.0.1로 접근 시 _next/* 리소스 허용
  // (localhost IPv6 포트 충돌 회피용)
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ]
  },
};

export default nextConfig;

import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // dev 서버에서 127.0.0.1로 접근 시 _next/* 리소스 허용
  // (localhost IPv6 포트 충돌 회피용)
  allowedDevOrigins: ['127.0.0.1', 'localhost'],
};

export default nextConfig;

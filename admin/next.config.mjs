// The dashboard is a client-side app that talks straight to Firebase, so it is exported as a static site
// (`npm run build` writes ./out) and hosted on Firebase Hosting: there is no server to run or secure.
// ADMIN_BUILD_DIR is only for the browser tests, which build into their own folder so they can never overwrite ./out.
/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  ...(process.env.ADMIN_BUILD_DIR ? { distDir: process.env.ADMIN_BUILD_DIR } : {}),
  trailingSlash: true,
  reactStrictMode: true,
  images: { unoptimized: true },
  poweredByHeader: false,
};

export default nextConfig;

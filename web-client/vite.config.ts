import { defineConfig } from 'vite';
export default defineConfig({server:{port:5173,proxy:{'/api':{target:'http://localhost:8080',changeOrigin:true,configure:proxy=>proxy.on('proxyReq',req=>req.setHeader('Origin','http://localhost:8080'))},'/health':'http://localhost:8080'}},build:{target:'es2022'}});

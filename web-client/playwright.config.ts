import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./e2e',timeout:90000,workers:1,use:{baseURL:process.env.TEST_BASE_URL??'http://localhost:8080',viewport:{width:1366,height:768},headless:true,channel:process.env.PLAYWRIGHT_CHANNEL??'chromium'},reporter:[['list'],['json',{outputFile:'test-results/results.json'}]],outputDir:'test-results/artifacts'});

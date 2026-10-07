import {chromium} from 'playwright';
import {readFileSync} from 'node:fs';
const baseURL=process.env.SHOWCASE_URL||'http://localhost:8083';
if(process.env.SHOWCASE_ACK!=='LOCAL_DISPOSABLE_ONLY'||!['localhost','127.0.0.1'].includes(new URL(baseURL).hostname))throw new Error('Showcase requires explicit disposable-loopback acknowledgement');
if(!process.env.OWNER_PASSWORD)throw new Error('Set OWNER_PASSWORD');
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH});
const owner=await browser.newContext({baseURL,viewport:{width:1440,height:1000}});const page=await owner.newPage();
async function login(p,user,password){await p.goto('/');await p.getByLabel('Username',{exact:true}).fill(user);await p.getByLabel('Password',{exact:true}).fill(password);await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Sign out'}).waitFor();}
await login(page,process.env.OWNER_USERNAME||'owner',process.env.OWNER_PASSWORD);
const csrf=(await(await page.request.get('/api/session')).json()).csrf;
async function call(url,body,method='POST'){const r=await page.request.fetch('/api'+url,{method,data:body,headers:{'X-CSRF-TOKEN':csrf}});if(!r.ok())throw new Error('Showcase request failed '+r.status()+' '+url);return r.json();}
const techPassword=process.env.DEMO_TECH_PASSWORD;if(!techPassword)throw new Error('Set DEMO_TECH_PASSWORD');
await call('/technicians',{username:'alex.morgan.demo',password:techPassword});await call('/technicians',{username:'sari.putri.demo',password:techPassword});
async function job(customer,location,invoice,units,technician='alex.morgan.demo'){return call('/jobs',{customer,location,invoice,units,technician});}
async function complete(j,count=j.units.length){for(const unit of j.units.slice(0,count)){j=await call(`/jobs/${j.id}/units/${unit.id}`,{version:j.version,cleaned:true,drainChecked:true,coolingChecked:true,actions:'Coil and filter cleaned. Drain flow clear; outlet air 17 °C. Cooling is stable with no follow-up required.'},'PUT');for(const kind of ['before','after']){const r=await page.request.post(`/api/jobs/${j.id}/units/${unit.id}/photos/${kind}`,{headers:{'X-CSRF-TOKEN':csrf},multipart:{version:String(j.version),key:crypto.randomUUID(),file:{name:`ac-${kind}.png`,mimeType:'image/png',buffer:readFileSync(`fixtures/ac-${kind}.png`)}}});if(!r.ok())throw new Error('Photo upload failed');j=await r.json();}}return j;}
async function move(j,action,reason=''){return call(`/jobs/${j.id}/${action}`,{version:j.version,reason});}
await job('Taman Residence','Bandung · Living area','INV-2026-045',['Living room · AC-01'],'sari.putri.demo');
let atlas=await complete(await job('Atlas Coworking','Kemang · Third floor','INV-2026-041',['Shared workspace · AC-01']));atlas=await move(await move(atlas,'submit'),'approve');
let harbor=await complete(await job('Harbor Coffee','Bandung · Front counter','INV-2026-043',['Front counter · AC-01']));harbor=await move(await move(harbor,'submit'),'request-changes','Please add the observed drain-flow result before resubmitting.');
const north=await complete(await job('North Studio','Bandung · Level 2','INV-2026-044',['Lobby · AC-01','Meeting room · AC-02']),1);
let meridian=await complete(await job('Meridian Office','Jakarta · West wing','INV-2026-046',['Reception · AC-01','Meeting room · AC-02']));meridian=await move(meridian,'submit');
await page.reload();await page.getByRole('button',{name:/Meridian Office/}).waitFor();await page.screenshot({path:'../evidence/owner-work-queue.png',fullPage:true});
await page.getByRole('button',{name:/Meridian Office/}).click();await page.locator('.unit img').first().waitFor();await page.locator('.unit img').evaluateAll(async images=>Promise.all(images.map(i=>i.decode())));await page.screenshot({path:'../evidence/owner-review.png',fullPage:true});
await page.getByRole('button',{name:'All jobs',exact:false}).click();await page.getByRole('button',{name:/Atlas Coworking/}).click();await page.getByRole('button',{name:'View approved report · Revision 1'}).click();await page.locator('.report img').evaluateAll(async images=>Promise.all(images.map(i=>i.decode())));await page.screenshot({path:'../evidence/approved-report.png',fullPage:true});await page.pdf({path:'../evidence/service-report.pdf',format:'A4',printBackground:true});
const mobile=await browser.newContext({baseURL,viewport:{width:390,height:844}});const tp=await mobile.newPage();await login(tp,'alex.morgan.demo',techPassword);await tp.getByRole('button',{name:/North Studio/}).click();await tp.locator('.unit img').first().waitFor();await tp.locator('.unit img').evaluateAll(async images=>Promise.all(images.map(i=>i.decode())));if(await tp.locator('body').evaluate(e=>e.scrollWidth)>390)throw new Error('Mobile overflow');await tp.screenshot({path:'../evidence/mobile-checklist.png',fullPage:true});
await browser.close();console.log('Captured real owner queue, review, report and mobile technician screens using clearly synthetic AC illustrations.');

// Explicitly opt in; this script refuses remote hosts and never runs at startup.
if (process.env.DEMO_ACK !== 'LOCAL_DISPOSABLE_ONLY') throw new Error('Set DEMO_ACK=LOCAL_DISPOSABLE_ONLY');
const base = new URL(process.env.DEMO_URL || 'http://localhost:8080');
if (!['localhost', '127.0.0.1', '[::1]'].includes(base.hostname)) throw new Error('Demo fixtures require a loopback host');
const {OWNER_USERNAME, OWNER_PASSWORD, DEMO_TECH_PASSWORD} = process.env;
if (!OWNER_USERNAME || !OWNER_PASSWORD || !DEMO_TECH_PASSWORD) throw new Error('Set OWNER_USERNAME, OWNER_PASSWORD and DEMO_TECH_PASSWORD');
let cookie = '';
async function call(path, method='GET', body, csrf) {
 const headers = {'Cookie': cookie}; if (csrf) headers['X-CSRF-TOKEN'] = csrf;
 if (body && !(body instanceof URLSearchParams)) {headers['Content-Type']='application/json';body=JSON.stringify(body);}
 const response = await fetch(new URL('/api'+path,base),{method,headers,body});
 for(const value of response.headers.getSetCookie()) if(value.startsWith('JSESSIONID=')) cookie=value.split(';')[0];
 if(!response.ok) throw new Error(`Demo request failed (${response.status}); existing data was not deleted.`);
 return response.status===204?null:response.json();
}
let session=await call('/session');
await call('/login','POST',new URLSearchParams({username:OWNER_USERNAME,password:OWNER_PASSWORD}),session.csrf);
session=await call('/session');
const username='demo-tech-'+Date.now().toString().slice(-7);
await call('/technicians','POST',{username,password:DEMO_TECH_PASSWORD},session.csrf);
await call('/jobs','POST',{customer:'North Studio · DEMO',location:'Bandung · Second floor · Fictional customer',invoice:'DEMO-INV-008',technician:username,units:['Reception · AC-01','Meeting room · AC-02']},session.csrf);
console.log(`Created a fictional two-unit job assigned to ${username}. No passwords printed.`);

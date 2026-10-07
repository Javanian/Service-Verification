import {readFileSync} from 'node:fs';
// Keep raw findings in the artifact. This one contextual assessment is guarded by
// a backend test that forbids XsltView and XsltViewResolver beans.
const failures=[];
for(const file of process.argv.slice(2)) {
 const report=JSON.parse(readFileSync(file,'utf8'));
 for(const result of report.Results || []) for(const v of result.Vulnerabilities || []) {
  if(!['HIGH','CRITICAL'].includes(v.Severity)) continue;
  if(v.VulnerabilityID==='CVE-2026-47884' && v.PkgName==='org.springframework:spring-webmvc' && v.InstalledVersion==='6.2.19') {
   console.log(`${file}: CVE-2026-47884 retained for review; XSLT view preconditions absent (docs/security-review.md).`);
  } else failures.push(`${file}: ${v.Severity} ${v.VulnerabilityID} ${v.PkgName}@${v.InstalledVersion}`);
 }
}
if(failures.length){console.error(failures.join('\n'));process.exit(1);}
console.log('No unassessed high/critical findings. Lower-severity findings remain in raw reports.');

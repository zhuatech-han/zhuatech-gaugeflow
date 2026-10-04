#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Isolated local MySQL acceptance. State is private, never printed or published."""
import argparse,datetime as dt,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
from pathlib import Path
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--run',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--url',default='http://127.0.0.1:8105');args=parser.parse_args()
assert urllib.parse.urlparse(args.url).hostname in ['127.0.0.1','localhost'], 'Local acceptance only'
assert args.run != args.verify,'Choose --run or --verify'
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
def now():return dt.datetime.now(dt.timezone.utc)
def key():return str(uuid.uuid4())
class Client:
    def __init__(self):self.op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
    def call(self,path,method='GET',body=None,expected=200):
        if self.csrf is None:self.csrf=self.call('/auth/csrf') if path!='/auth/csrf' else {}
        headers={'Content-Type':'application/json'}
        if method!='GET':headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(args.url+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with self.op.open(req,timeout=30) as res:code=res.status;value=json.load(res)
        except urllib.error.HTTPError as res:code=res.code;value=json.load(res)
        assert code==expected,f'{method} {path}: {code}, expected {expected}, code={value.get("code") if isinstance(value,dict) else None}'
        return value
    def login(self,name,password):self.call('/auth/login','POST',{'username':name,'password':password})
admin=Client();admin.login('admin',env['ADMIN_PASSWORD']);statefile=root/'.smoke-state.json'
if args.run:
    assert not statefile.exists(),'Do not overwrite acceptance state'
    assert admin.call('/gauges')['total']==0,'Fresh database required'
    state={'suffix':secrets.token_hex(4),'password':'Aa9'+secrets.token_urlsafe(24)};s=state['suffix']
    dept=admin.call('/admin/departments','POST',{'name':'验收测试 · 计量室-'+s})['id'];state['departmentId']=dept
    roles={r['name']:r['id'] for r in admin.call('/admin/roles')}
    def user(name,label,role,department):
        return admin.call('/admin/users','POST',{'username':name,'displayName':label,'password':state['password'],'roleId':roles[role],'departmentId':department,'enabled':True})['id']
    state['writer']='meter-'+s;state['reviewer']='review-'+s;state['outsider']='outside-'+s
    state['writerId']=user(state['writer'],'验收测试 · 计量员','计量员',dept);user(state['reviewer'],'验收测试 · 质量复核员','质量复核员',dept);user(state['outsider'],'验收测试 · 外部门使用员','使用人员',1)
    writer=Client();writer.login(state['writer'],state['password']);reviewer=Client();reviewer.login(state['reviewer'],state['password']);outside=Client();outside.login(state['outsider'],state['password'])
    g=writer.call('/gauges','POST',{'code':'TEST-G-'+s,'name':'验收测试 · 千分尺','model':'0–25 mm','category':'LENGTH','departmentId':dept});gid=g['id'];state['gaugeId']=gid
    def gauge():return writer.call('/gauges/'+str(gid))['record']
    def cmd(c,kind,r,a,extra=None):return c.call(f'/{kind}/{r["id"]}/commands/{a}','POST',dict({'version':r['version'],'requestKey':key(),'note':'验收测试：核对报告、范围及证据'},**(extra or {})))
    def cal(result,checked,report):return writer.call('/calibrations','POST',{'gaugeId':gid,'version':gauge()['version'],'reportNo':report,'provider':'验收测试 · 校准机构','checkedAt':checked.isoformat(),'validUntil':(now()+dt.timedelta(days=90)).date().isoformat() if result=='PASS' else None,'result':result,'evidence':'验收测试：报告点位摘要及证据引用，不代表真实校准证书','requestKey':key()})
    c=cal('PASS',now()-dt.timedelta(days=2),'TEST-PASS-'+s);cmd(reviewer,'calibrations',c,'approve')
    use={'gaugeId':gid,'version':gauge()['version'],'batchRef':'验收测试 · B-01','taskRef':'验收测试 · Q-01','product':'验收测试 · 标准件','note':'验收测试测量记录','requestKey':key()}
    writer.call('/uses','POST',use);writer.call('/uses','POST',use);assert writer.call('/uses')['total']==1
    c=cal('FAIL',now()-dt.timedelta(seconds=1),'TEST-FAIL-'+s)
    assert gauge()['status']=='QUARANTINED'
    writer.call('/uses','POST',dict(use,version=gauge()['version'],requestKey=key()),409)
    cmd(reviewer,'calibrations',c,'approve')
    i=writer.call('/incidents')['items'][0];state['incidentId']=i['id'];d=writer.call('/incidents/'+str(i['id']));assert len(d['impacts'])==1
    outside.call('/incidents/'+str(i['id']),expected=403);outside.call('/admin/users',expected=403)
    assert outside.call('/gauges')['total']==0
    cmd(writer,'incidents',i,'submit') if not d['impacts'] else None
    writer.call(f'/incidents/{i["id"]}/commands/submit','POST',dict(version=i['version'],requestKey=key(),note='验收测试范围'),409)
    p=d['impacts'][0]['impact'];p=cmd(writer,'impacts',p,'assign',{'assigneeId':state['writerId']});p=cmd(writer,'impacts',p,'assess',{'resolution':'RETEST_PASS','note':'验收测试：更换合格量具后复检通过，记录引用 TEST-RETEST-01'})
    i=writer.call('/incidents/'+str(i['id']))['record'];i=cmd(writer,'incidents',i,'submit');i=cmd(reviewer,'incidents',i,'return')
    i=cmd(writer,'incidents',i,'submit');i=cmd(reviewer,'incidents',i,'close')
    writer.call(f'/gauges/{gid}/commands/release','POST',dict(version=gauge()['version'],requestKey=key(),note='验收测试恢复'),409)
    c=cal('PASS',now(),'TEST-RECOVERY-'+s);cmd(reviewer,'calibrations',c,'approve');assert gauge()['status']=='QUARANTINED';cmd(writer,'gauges',gauge(),'release')
    assert gauge()['status']=='AVAILABLE';writer.call('/uses','POST',dict(use,version=gauge()['version'],batchRef='验收测试 · B-02',taskRef='验收测试 · Q-02',requestKey=key()))
    report=writer.call(f'/incidents/{state["incidentId"]}/report.json');assert report['record']['status']=='CLOSED'
    fd=os.open(statefile,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as f:json.dump(state,f)
    print(json.dumps({'freshLogin':'PASS','independentCalibration':'PASS','freezeAndTrace':'PASS','assessmentReturnClose':'PASS','recoveryGate':'PASS','idempotency':'PASS','departmentAndAdminDenial':'PASS','usageRecords':2,'closedIncidents':1}))
else:
    state=json.loads(statefile.read_text());writer=Client();writer.login(state['writer'],state['password']);d=writer.call('/gauges/'+str(state['gaugeId']));assert d['record']['status']=='AVAILABLE';assert len(d['calibrations'])>=3
    i=writer.call('/incidents/'+str(state['incidentId']));assert i['record']['status']=='CLOSED';assert i['impacts'][0]['impact']['resolution']=='RETEST_PASS';assert writer.call('/uses')['total']>=2
    print(json.dumps({'restartPersistence':'PASS','persistedReports':len(d['calibrations']),'usageRecords':writer.call('/uses')['total'],'closedIncidentAndAssessment':'PASS'}))

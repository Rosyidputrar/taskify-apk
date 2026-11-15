import { createVault, encryptItem, encryptTotpLog, encryptConfig, keyRef, type ItemPayload } from './crypto.ts';
const USER = '11111111-2222-3333-4444-555555555555';
const PASSWORD = 'ｋｕｄａ-biru minum kopi pagi ☕ Ünïcödé';
const v = await createVault(PASSWORD, USER, { iterations: 100_000 });
const ref = await keyRef(v.record.wrapped_key);
const item: ItemPayload = { title: 'GitHub', url: 'https://github.com/login', username: 'rosyid', password: 'Zk9#mQ2$vL7!pXr4 ünï', notes: 'baris 1\nbaris 2 "kutip"', favorite: true, totp: { secret: 'JBSWY3DPEHPK3PXP', digits: 6, period: 30, algorithm: 'SHA1' } };
const out = {
  user: USER, password: PASSWORD, recoveryKey: v.recoveryKey, record: v.record, ref,
  item: { id: 'item-1', enc: await encryptItem(v.dek, USER, 'item-1', item, ref), payload: item },
  itemNoTotp: { id: 'item-2', enc: await encryptItem(v.dek, USER, 'item-2', { ...item, title: 'Tanpa TOTP', totp: null, favorite: false }, ref) },
  log: { id: 'log-1', enc: await encryptTotpLog(v.dek, USER, 'log-1', { itemId: 'item-1', name: 'GitHub', code: '123456', usedAt: '2026-10-07T01:02:03.000Z' }, ref) },
  config: { fileId: 'file-1', versionId: 'ver-1', enc: await encryptConfig(v.dek, USER, 'file-1', 'ver-1', { text: 'A=1\n# komentar\nB="x y"', note: 'ganti URL' }, ref) },
};
console.log(JSON.stringify(out, null, 2));

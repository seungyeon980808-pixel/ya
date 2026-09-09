const ALLOWED_EMAIL = 'owner@example.invalid';
const SHEET_HEADERS = {
  Items: ['itemId','status','sourceType','area','kind','title','startAt','endAt','reminderAt','transcript','notes','createdAt','updatedAt','approvedAt','createdBy','approvedBy','version','calendarEnabled','calendarId','calendarEventId','deletedAt','idempotencyKey','syncState'],
  Attachments: ['attachmentId','itemId','sourceType','mimeType','driveFileId','localFileName','originalFileName','sha256','sizeBytes','durationMs','createdAt','syncState'],
  AuditLog: ['auditId','itemId','action','actorEmail','at','priorVersion','newVersion','detail'],
  Settings: ['key','value']
};

function doGet() {
  assertOwner_();
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('말해둬 승인함')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.DEFAULT);
}

function getBootstrap() {
  const email = assertOwner_();
  ensureSheets_();
  return {
    email,
    items: listItems_(),
    settings: settingsObject_(),
    serverTime: new Date().toISOString()
  };
}

function createItem(input) {
  const email = assertOwner_();
  return withLock_(() => {
    ensureSheets_();
    const now = new Date().toISOString();
    const item = {
      itemId: Utilities.getUuid(), status: 'PENDING', sourceType: clean_(input.sourceType || 'WEB'),
      area: enum_(input.area, ['PERSONAL','SCHOOL'], 'PERSONAL'),
      kind: enum_(input.kind, ['SCHEDULE','TODO'], 'SCHEDULE'),
      title: required_(input.title, '제목'), startAt: clean_(input.startAt), endAt: clean_(input.endAt),
      reminderAt: clean_(input.reminderAt), transcript: clean_(input.transcript), notes: clean_(input.notes),
      createdAt: now, updatedAt: now, approvedAt: '', createdBy: email, approvedBy: '', version: 1,
      calendarEnabled: bool_(input.calendarEnabled), calendarId: clean_(input.calendarId),
      calendarEventId: '', deletedAt: '', idempotencyKey: clean_(input.idempotencyKey || Utilities.getUuid()),
      syncState: 'SYNCED'
    };
    validateItem_(item);
    appendObject_('Items', item);
    audit_({itemId: item.itemId, action: 'CREATE_PENDING', actor: email, priorVersion: 0, newVersion: 1, detail: item.sourceType});
    return item;
  });
}

function updateItem(input) {
  const email = assertOwner_();
  return withLock_(() => {
    const record = findItem_(input.itemId);
    const item = record.item;
    requireVersion_(item, input.version);
    if (item.status === 'DELETED') throw new Error('삭제된 항목입니다.');
    const prior = number_(item.version, 1);
    item.area = enum_(input.area, ['PERSONAL','SCHOOL'], item.area || 'PERSONAL');
    item.kind = enum_(input.kind, ['SCHEDULE','TODO'], item.kind || 'SCHEDULE');
    item.title = required_(input.title, '제목');
    item.startAt = clean_(input.startAt); item.endAt = clean_(input.endAt); item.reminderAt = clean_(input.reminderAt);
    item.notes = clean_(input.notes); item.calendarEnabled = bool_(input.calendarEnabled);
    item.calendarId = clean_(input.calendarId); item.updatedAt = new Date().toISOString(); item.version = prior + 1;
    validateItem_(item);
    writeObject_(record.sheet, record.row, item);
    if (item.status === 'APPROVED' && item.calendarEventId) exportCalendar_(item, record.sheet, record.row);
    audit_({itemId: item.itemId, action: 'EDIT_' + item.status, actor: email, priorVersion: prior, newVersion: item.version, detail: ''});
    return item;
  });
}

function approveItem(itemId, expectedVersion) {
  const email = assertOwner_();
  return withLock_(() => {
    const record = findItem_(itemId);
    const item = record.item;
    requireVersion_(item, expectedVersion);
    if (item.status !== 'PENDING') throw new Error('이미 처리된 항목입니다.');
    validateItem_(item);
    const prior = number_(item.version, 1);
    const now = new Date().toISOString();
    item.status = 'APPROVED'; item.approvedAt = now; item.approvedBy = email;
    item.updatedAt = now; item.version = prior + 1;
    writeObject_(record.sheet, record.row, item);
    audit_({itemId: item.itemId, action: 'APPROVE', actor: email, priorVersion: prior, newVersion: item.version, detail: 'calendar=' + bool_(item.calendarEnabled)});
    if (bool_(item.calendarEnabled)) exportCalendar_(item, record.sheet, record.row);
    return findItem_(itemId).item;
  });
}

function deleteItem(itemId, expectedVersion) {
  const email = assertOwner_();
  return withLock_(() => {
    const record = findItem_(itemId);
    const item = record.item;
    requireVersion_(item, expectedVersion);
    const prior = number_(item.version, 1);
    removeCalendar_(item);
    const now = new Date().toISOString();
    item.status = 'DELETED'; item.deletedAt = now; item.updatedAt = now;
    item.version = prior + 1; item.calendarEventId = '';
    writeObject_(record.sheet, record.row, item);
    audit_({itemId: item.itemId, action: 'DELETE', actor: email, priorVersion: prior, newVersion: item.version, detail: 'source retained'});
    return {itemId, status: 'DELETED', version: item.version};
  });
}

function listItems_() {
  const sheet = sheet_('Items');
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];
  const headers = values[0].map(String);
  return values.slice(1).map(row => rowObject_(headers, row))
    .filter(item => item.itemId && item.status !== 'DELETED')
    .map(serializable_)
    .sort((a,b) => String(b.createdAt).localeCompare(String(a.createdAt)));
}

function exportCalendar_(item, sheet, row) {
  if (!bool_(item.calendarEnabled) || item.kind !== 'SCHEDULE' || !item.startAt) return;
  const settings = settingsObject_();
  const calendarId = clean_(item.calendarId || settings.defaultCalendarId);
  if (!calendarId) return;
  const calendar = CalendarApp.getCalendarById(calendarId);
  if (!calendar) throw new Error('선택한 캘린더를 찾을 수 없습니다.');
  const start = new Date(item.startAt);
  const end = item.endAt ? new Date(item.endAt) : new Date(start.getTime() + 60 * 60 * 1000);
  let event = item.calendarEventId ? calendar.getEventById(String(item.calendarEventId)) : null;
  const description = '말해둬에서 승인됨\n\n음성 인식: ' + clean_(item.transcript) + '\n\n메모: ' + clean_(item.notes);
  if (event) {
    event.setTitle(item.title).setTime(start, end).setDescription(description);
  } else {
    event = calendar.createEvent(item.title, start, end, {description});
    item.calendarEventId = event.getId();
    item.calendarId = calendarId;
    item.updatedAt = new Date().toISOString();
    item.version = number_(item.version, 1) + 1;
    writeObject_(sheet, row, item);
    audit_({itemId: item.itemId, action: 'CALENDAR_CREATED', actor: assertOwner_(), priorVersion: item.version - 1, newVersion: item.version, detail: calendarId});
  }
}

function removeCalendar_(item) {
  if (!item.calendarEventId || !item.calendarId) return;
  try {
    const calendar = CalendarApp.getCalendarById(String(item.calendarId));
    const event = calendar && calendar.getEventById(String(item.calendarEventId));
    if (event) event.deleteEvent();
  } catch (error) {
    console.error('Calendar cleanup failed', error);
  }
}

function ensureSheets_() {
  const ss = SpreadsheetApp.getActive();
  Object.keys(SHEET_HEADERS).forEach(name => {
    let sheet = ss.getSheetByName(name);
    if (!sheet) {
      const firstHeader = SHEET_HEADERS[name][0];
      sheet = ss.getSheets().find(s => clean_(s.getRange(1,1).getDisplayValue()) === firstHeader) || null;
      if (sheet) sheet.setName(name);
    }
    if (!sheet) sheet = ss.insertSheet(name);
    const headers = SHEET_HEADERS[name];
    if (sheet.getLastRow() === 0) sheet.getRange(1,1,1,headers.length).setValues([headers]);
    assertHeaders_(sheet, name);
    sheet.setFrozenRows(1);
  });
}

function sheet_(name, required) {
  const ss = SpreadsheetApp.getActive();
  let found = ss.getSheetByName(name);
  if (!found) {
    const firstHeader = SHEET_HEADERS[name] && SHEET_HEADERS[name][0];
    found = ss.getSheets().find(s => clean_(s.getRange(1,1).getDisplayValue()) === firstHeader) || null;
  }
  if (!found && required !== false) throw new Error(name + ' 시트를 찾을 수 없습니다.');
  return found;
}

function findItem_(itemId) {
  const sheet = sheet_('Items');
  const values = sheet.getDataRange().getValues();
  if (!values.length) throw new Error('데이터가 없습니다.');
  const headers = values[0].map(String);
  const idIndex = headers.indexOf('itemId');
  for (let i=1;i<values.length;i++) if (String(values[i][idIndex]) === String(itemId)) {
    return {sheet, row: i+1, item: serializable_(rowObject_(headers, values[i]))};
  }
  throw new Error('항목을 찾을 수 없습니다.');
}

function appendObject_(name, object) {
  const sheet = sheet_(name); assertHeaders_(sheet, name);
  const headers = SHEET_HEADERS[name];
  sheet.appendRow(headers.map(h => valueForCell_(object[h])));
}

function writeObject_(sheet, row, object) {
  assertHeaders_(sheet, 'Items');
  validateItem_(object);
  const existingId = clean_(sheet.getRange(row, 1).getDisplayValue());
  if (existingId !== clean_(object.itemId)) throw new Error('수정 대상 행의 항목 ID가 일치하지 않습니다.');
  const headers = SHEET_HEADERS.Items;
  sheet.getRange(row,1,1,headers.length).setValues([headers.map(h => valueForCell_(object[h]))]);
}

function audit_(record) {
  const itemId = clean_(record && record.itemId);
  const action = clean_(record && record.action);
  const actor = clean_(record && record.actor);
  const priorVersion = number_(record && record.priorVersion, NaN);
  const newVersion = number_(record && record.newVersion, NaN);
  if (!itemId || !/^[A-Z][A-Z0-9_]*$/.test(action) || actor !== ALLOWED_EMAIL ||
      !Number.isFinite(priorVersion) || !Number.isFinite(newVersion) || newVersion < priorVersion) {
    throw new Error('감사 기록 인자가 올바르지 않습니다.');
  }
  const sheet = sheet_('AuditLog');
  assertHeaders_(sheet, 'AuditLog');
  sheet.appendRow([
    Utilities.getUuid(), itemId, action, actor, new Date().toISOString(),
    String(priorVersion), String(newVersion), clean_(record.detail)
  ]);
}

function settingsObject_() {
  const sheet = sheet_('Settings'); const values = sheet.getDataRange().getDisplayValues(); const out = {};
  values.slice(1).forEach(r => { if (r[0]) out[r[0]] = r[1]; });
  return out;
}

function headers_(sheet) { return sheet.getRange(1,1,1,Math.max(1,sheet.getLastColumn())).getDisplayValues()[0].map(String); }
function assertHeaders_(sheet,name) {
  const expected = SHEET_HEADERS[name];
  if (!expected) throw new Error('알 수 없는 시트입니다: ' + name);
  const actual = sheet.getRange(1,1,1,expected.length).getDisplayValues()[0].map(String);
  if (actual.join('\u001f') !== expected.join('\u001f')) throw new Error(name + ' 시트 헤더가 손상되었습니다.');
}
function rowObject_(headers,row) { const out={}; headers.forEach((h,i)=>out[h]=row[i]); return out; }
function serializable_(obj) { const out={}; Object.keys(obj).forEach(k=>out[k]=obj[k] instanceof Date?obj[k].toISOString():obj[k]); return out; }
function valueForCell_(v) { return v === undefined || v === null ? '' : v; }
function withLock_(fn) { const lock=LockService.getDocumentLock(); lock.waitLock(20000); try{return fn();}finally{lock.releaseLock();} }
function assertOwner_() { const email=Session.getActiveUser().getEmail(); if(email!==ALLOWED_EMAIL) throw new Error('허용되지 않은 계정입니다.'); return email; }
function requireVersion_(item, expected) { if(number_(item.version,1)!==number_(expected,0)) throw new Error('다른 화면에서 수정되었습니다. 새로고침 후 다시 시도해주세요.'); }
function validateItem_(item) {
  if(!clean_(item.itemId)) throw new Error('항목 ID가 필요합니다.');
  if(['PENDING','APPROVED','DONE','DELETED'].indexOf(clean_(item.status)) < 0) throw new Error('항목 상태가 올바르지 않습니다.');
  if(!Number.isFinite(number_(item.version, NaN)) || number_(item.version, 0) < 1) throw new Error('버전 값이 올바르지 않습니다.');
  if(item.status !== 'DELETED' && !clean_(item.title)) throw new Error('제목이 필요합니다.');
  if(item.status !== 'DELETED' && item.kind==='SCHEDULE'&&!clean_(item.startAt)) throw new Error('일정에는 날짜와 시간이 필요합니다.');
}
function required_(v,label){const s=clean_(v);if(!s)throw new Error(label+'이 필요합니다.');return s;}
function clean_(v){return v===undefined||v===null?'':String(v).trim();}
function enum_(v,values,fallback){const s=clean_(v);return values.indexOf(s)>=0?s:fallback;}
function bool_(v){return v===true||String(v).toUpperCase()==='TRUE';}
function number_(v,fallback){const n=Number(v);return Number.isFinite(n)?n:fallback;}

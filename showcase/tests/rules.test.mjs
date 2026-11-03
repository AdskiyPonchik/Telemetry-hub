import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {evaluateReading,breachedRules} from '../rules.mjs';
const fixture = JSON.parse(await readFile(new URL('../../tests/fixtures/classification-cases.json',import.meta.url),'utf8'));
for(const item of fixture.cases) test(item.name,()=>{const actual=evaluateReading(item.input,fixture.thresholds);assert.equal(actual.httpStatus,item.httpStatus);assert.equal(actual.status,item.status);});
test('breach explanation lists only actually violated rules',()=>{assert.deepEqual(breachedRules({voltage:260,frequency:50,temperature:95,vibration:2},fixture.thresholds),[{metric:'voltage',operator:'>',limit:242},{metric:'temperature',operator:'>',limit:80}]);});
test('non-finite browser values cannot be accepted',()=>{const normal={eventId:'6aef80c8-16bf-4bfd-b392-cbda5f12bb25',sensorId:'test',voltage:220,current:10,frequency:50,temperature:40,vibration:1};for(const value of [NaN,Infinity,-Infinity])assert.equal(evaluateReading({...normal,current:value}).httpStatus,400);});

test('request UUID validation rejects malformed identities without hiding valid measurements', () => {
    const normal = fixture.cases.find(item => item.name === 'normal').input;
    for (const eventId of ['', 'not-a-uuid', 12, '6aef80c8-16bf-4bfd-b392-cbda5f12bb2']) {
        assert.deepEqual(evaluateReading({...normal, eventId}).errors, ['eventId']);
    }
    assert.equal(evaluateReading({...normal, eventId: normal.eventId.toUpperCase()}).httpStatus, 201);
});

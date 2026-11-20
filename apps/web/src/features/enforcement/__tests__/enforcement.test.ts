import { nextActions } from '../viewmodel/useEnforcementViewModel';

describe('case state machine (UI mirror)', () => {
  it.each([
    ['OPEN', ['sanction', 'dismiss']],
    ['SANCTIONED', ['appeal', 'close']],
    ['APPEALED', ['appeal-decision']],
    ['OVERTURNED', ['close']],
    ['CLOSED', []],
  ])('%s → %j', (status, actions) => {
    expect(nextActions(status)).toEqual(actions);
  });
});

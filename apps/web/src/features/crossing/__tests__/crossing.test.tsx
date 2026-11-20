import { act, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axeViolations, mockFetch, Providers, renderWithProviders } from '../../../test/utils';
import { CrossingPage } from '../view/CrossingPage';
import { useCrossingViewModel } from '../viewmodel/useCrossingViewModel';

const ALLOW = {
  personId: '11111111-1111-1111-1111-111111111111', nationalId: '900000001', fullName: 'Demo Person',
  decision: 'ALLOW', reasons: ['VALID_PERMIT:WORK_ENTRY:2027-01-10'], policyVersion: 'work-v3',
  checkedAt: '2026-11-20T10:00:00Z', cacheHit: true,
};

describe('crossing viewmodel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('keeps only digits and max 9 characters in the id', () => {
    const { result } = renderHook(() => useCrossingViewModel(), { wrapper: Providers });
    act(() => result.current.setNationalId('90-00a00001234'));
    expect(result.current.nationalId).toBe('900000001');
    expect(result.current.idValid).toBe(true);
  });

  it('maps an ALLOW response to a green verdict with translated reasons', async () => {
    mockFetch({ 'GET /api/eligibility/check': ALLOW });
    const { result } = renderHook(() => useCrossingViewModel(), { wrapper: Providers });
    act(() => result.current.setNationalId('900000001'));
    act(() => result.current.runCheck());
    await waitFor(() => expect(result.current.result).not.toBeNull());
    expect(result.current.result!.tone).toBe('allow');
    expect(result.current.result!.reasons[0]).toContain('כניסה לעבודה'); // he translation of WORK_ENTRY
    expect(result.current.result!.cacheHit).toBe(true);
  });
});

describe('crossing view', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('renders an accessible form and shows the verdict', async () => {
    mockFetch({ 'GET /api/eligibility/check': { ...ALLOW, decision: 'DENY', reasons: ['NO_VALID_PERMIT'] } });
    const { container } = renderWithProviders(<CrossingPage />);
    expect(await axeViolations(container)).toEqual([]);
    await userEvent.type(screen.getByLabelText(/מספר זהות/), '900000005');
    await userEvent.click(screen.getByRole('button', { name: 'בדיקה' }));
    expect(await screen.findByText(/לא מאושר/)).toBeInTheDocument();
    expect(screen.getByText('אין היתר בתוקף')).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });
});

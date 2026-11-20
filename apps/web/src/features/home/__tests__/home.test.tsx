import { screen } from '@testing-library/react';
import { axeViolations, renderWithProviders } from '../../../test/utils';
import { AccessibilityPage } from '../view/AccessibilityPage';
import { HomePage } from '../view/HomePage';

describe('home & accessibility pages', () => {
  it('home (signed out) offers login and has no axe violations', async () => {
    const { container } = renderWithProviders(<HomePage />);
    expect(screen.getAllByRole('button', { name: 'כניסה למערכת' }).length).toBeGreaterThan(0);
    expect(await axeViolations(container)).toEqual([]);
  });

  it('accessibility statement mentions IS 5568 and has no axe violations', async () => {
    const { container } = renderWithProviders(<AccessibilityPage />);
    expect(screen.getByText(/5568/)).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
  });
});

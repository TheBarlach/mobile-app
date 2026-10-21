
import { describe, expect, it } from '@jest/globals';
import { splitExpense } from './splitExpense';

describe('splitExpense', () => {
  it('splits an expense equally', () => {
    expect(splitExpense(9000, 3)).toEqual([
      3000, 3000, 3000
    ]);
  });

  it('handles rounding correctly', () => {
    expect(splitExpense(10000, 3)).toEqual([
      3334, 3333, 3333
    ]);
  });

  it('rejects zero participants', () => {
    expect(() => splitExpense(1000, 0)).toThrow();
  });

  it('rejects negative amounts', () => {
    expect(() => splitExpense(-100, 2)).toThrow();
  });
});

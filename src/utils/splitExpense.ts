
export function splitExpense(
  amount: number,
  participants: number
): number[] {
  if (
    !Number.isSafeInteger(amount) ||
    amount < 0 ||
    !Number.isInteger(participants) ||
    participants <= 0
  ) {
    throw new Error("Invalid expense or participants");
  }

  const base = Math.floor(amount / participants);
  const remainder = amount % participants;

  return Array.from(
    { length: participants },
    (_, index) => base + (index < remainder ? 1 : 0)
  );
}

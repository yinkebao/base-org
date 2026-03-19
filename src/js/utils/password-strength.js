export function getPasswordStrength(password) {
  const value = String(password || "");

  if (!value) {
    return { level: 0, text: "无" };
  }

  let score = 0;
  if (value.length >= 8) score += 1;
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1;
  if (/\d/.test(value)) score += 1;
  if (/[^A-Za-z\d]/.test(value)) score += 1;

  if (score <= 1) return { level: 1, text: "弱" };
  if (score <= 3) return { level: 2, text: "中等" };
  return { level: 3, text: "强" };
}

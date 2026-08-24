export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 128

export interface PasswordRequirementStatus {
  minLength: boolean
  hasUppercase: boolean
  hasNumber: boolean
}

export function passwordRequirementStatus(password: string): PasswordRequirementStatus {
  return {
    minLength: password.length >= PASSWORD_MIN_LENGTH && password.length <= PASSWORD_MAX_LENGTH,
    hasUppercase: /[A-Z]/.test(password),
    hasNumber: /[0-9]/.test(password),
  }
}

export function meetsPasswordRequirements(password: string): boolean {
  const status = passwordRequirementStatus(password)
  return status.minLength && status.hasUppercase && status.hasNumber
}

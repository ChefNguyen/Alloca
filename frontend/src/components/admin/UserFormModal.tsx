import React, { useState } from 'react';
import { DepartmentSummary, Role, User } from '@/types/api';

export interface UserFormValues {
  username: string;
  email: string;
  password?: string;
  fullName: string;
  role: Role;
  departmentId: string | null;
}

interface UserFormModalProps {
  isOpen: boolean;
  user: User | null; // null = create mode, non-null = edit mode
  departments: DepartmentSummary[];
  submitting?: boolean;
  serverErrors?: Record<string, string>;
  serverError?: string | null;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => void;
}

const ROLES: { value: Role; label: string; desc: string }[] = [
  { value: 'EMPLOYEE', label: 'Employee', desc: 'Self-service reservations only' },
  { value: 'MANAGER', label: 'Manager', desc: 'Department-scoped management' },
  { value: 'ADMIN', label: 'Admin', desc: 'Full workspace governance' },
];

const inputClass =
  'w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0';
const labelClass = 'block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1';

const toFormValues = (user: User | null): UserFormValues => ({
  username: user?.username ?? '',
  email: user?.email ?? '',
  fullName: user?.fullName ?? '',
  password: '',
  role: user?.role ?? 'EMPLOYEE',
  departmentId: user?.department?.id ?? null,
});

const validate = (values: UserFormValues, isEdit: boolean): Record<string, string> => {
  const errors: Record<string, string> = {};
  const username = values.username.trim();
  if (!isEdit) {
    if (!username) errors.username = 'Username is required.';
    else if (username.length < 3 || username.length > 50) {
      errors.username = 'Username must be between 3 and 50 characters.';
    } else if (!/^[a-zA-Z0-9._-]+$/.test(username)) {
      errors.username = 'Only alphanumeric, dots, underscores, and hyphens.';
    }

    if (!values.password) {
      errors.password = 'Password is required.';
    } else if (values.password.length < 8) {
      errors.password = 'Password must be at least 8 characters.';
    }
  }

  const email = values.email.trim();
  if (!email) errors.email = 'Email is required.';
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'Invalid email address.';
  }

  const fullName = values.fullName.trim();
  if (!fullName) errors.fullName = 'Full name is required.';
  else if (fullName.length > 100) errors.fullName = 'Full name must be at most 100 characters.';

  return errors;
};

export const UserFormModal: React.FC<UserFormModalProps> = ({
  isOpen,
  user,
  departments,
  submitting = false,
  serverErrors = {},
  serverError = null,
  onClose,
  onSubmit,
}) => {
  const isEdit = user !== null;
  const [values, setValues] = useState<UserFormValues>(() => toFormValues(user));
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});

  if (!isOpen) return null;

  const errors = { ...clientErrors, ...serverErrors };

  const update = <K extends keyof UserFormValues>(field: K, val: UserFormValues[K]) => {
    const next = { ...values, [field]: val };
    setValues(next);
    if (touched[field]) {
      setClientErrors(validate(next, isEdit));
    }
  };

  const handleBlur = (field: keyof UserFormValues) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    setClientErrors(validate(values, isEdit));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const vErrors = validate(values, isEdit);
    setClientErrors(vErrors);
    setTouched({
      username: true,
      email: true,
      fullName: true,
      password: true,
    });
    if (Object.keys(vErrors).length === 0) {
      onSubmit(values);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#242424]/40 backdrop-blur-xs"
    >
      <div className="w-full max-w-lg bg-[#FAFAF8] rounded-[8px] shadow-card border-0 p-6 space-y-5 max-h-[90vh] overflow-y-auto">
        <div className="flex items-start justify-between pb-3 border-b border-[#E5E0D8]/60">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              {isEdit ? 'Directory Governance' : 'Account Provisioning'}
            </span>
            <h2 className="text-xl font-light text-[#242424] font-editorial mt-0.5">
              {isEdit ? `Edit User: ${user.fullName}` : 'Add New User'}
            </h2>
          </div>
          <button
            onClick={onClose}
            disabled={submitting}
            className="text-[#8A8070] hover:text-[#242424] text-lg font-bold px-2 py-1 cursor-pointer"
          >
            ✕
          </button>
        </div>

        {serverError && (
          <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
            {serverError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Username (only editable in create mode) */}
          <div>
            <label className={labelClass}>Username</label>
            <input
              type="text"
              value={values.username}
              disabled={isEdit || submitting}
              onChange={(e) => update('username', e.target.value)}
              onBlur={() => handleBlur('username')}
              placeholder="e.g. john_doe"
              className={`${inputClass} ${isEdit ? 'opacity-60 cursor-not-allowed' : ''}`}
            />
            {errors.username && <p className="text-[11px] text-[#E05252] mt-1">{errors.username}</p>}
          </div>

          {/* Full Name */}
          <div>
            <label className={labelClass}>Full Name</label>
            <input
              type="text"
              value={values.fullName}
              disabled={submitting}
              onChange={(e) => update('fullName', e.target.value)}
              onBlur={() => handleBlur('fullName')}
              placeholder="e.g. John Doe"
              className={inputClass}
            />
            {errors.fullName && <p className="text-[11px] text-[#E05252] mt-1">{errors.fullName}</p>}
          </div>

          {/* Email */}
          <div>
            <label className={labelClass}>Email Address</label>
            <input
              type="email"
              value={values.email}
              disabled={submitting}
              onChange={(e) => update('email', e.target.value)}
              onBlur={() => handleBlur('email')}
              placeholder="john.doe@company.com"
              className={inputClass}
            />
            {errors.email && <p className="text-[11px] text-[#E05252] mt-1">{errors.email}</p>}
          </div>

          {/* Password (required for create, optional for edit) */}
          <div>
            <label className={labelClass}>{isEdit ? 'New Password (leave empty to keep current)' : 'Password'}</label>
            <input
              type="password"
              value={values.password ?? ''}
              disabled={submitting}
              onChange={(e) => update('password', e.target.value)}
              onBlur={() => handleBlur('password')}
              placeholder={isEdit ? '••••••••' : 'Min 8 characters'}
              className={inputClass}
            />
            {errors.password && <p className="text-[11px] text-[#E05252] mt-1">{errors.password}</p>}
          </div>

          {/* Role Selection */}
          <div>
            <label className={labelClass}>System Role</label>
            <div className="grid grid-cols-3 gap-2 mt-1">
              {ROLES.map((r) => (
                <button
                  type="button"
                  key={r.value}
                  onClick={() => update('role', r.value)}
                  className={`p-2.5 rounded-[6px] text-left transition-all border ${
                    values.role === r.value
                      ? 'border-[#242424] bg-[#242424] text-white shadow-xs'
                      : 'border-[#E5E0D8] bg-[#F0EDE8]/40 text-[#242424] hover:bg-[#F0EDE8]'
                  }`}
                >
                  <div className="text-xs font-bold">{r.label}</div>
                  <div className={`text-[10px] line-clamp-1 ${values.role === r.value ? 'text-[#E5E0D8]' : 'text-[#8A8070]'}`}>
                    {r.desc}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Department Selection */}
          <div>
            <label className={labelClass}>Department</label>
            <select
              value={values.departmentId ?? ''}
              disabled={submitting}
              onChange={(e) => update('departmentId', e.target.value ? e.target.value : null)}
              className={inputClass}
            >
              <option value="">No Department (Global / Unassigned)</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-[#E5E0D8]/60">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2.5 rounded-[6px] text-xs font-bold text-[#8A8070] hover:text-[#242424] cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2.5 rounded-[6px] bg-[#D97757] text-white text-xs font-bold shadow-card hover:opacity-95 transition-all cursor-pointer disabled:opacity-50"
            >
              {submitting ? 'Saving...' : isEdit ? 'Update User' : 'Create User'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

import React, { useState } from 'react';

export interface SimpleField {
  name: string;
  label: string;
  maxLength: number;
  placeholder?: string;
}

interface SimpleFormModalProps {
  eyebrow: string;
  title: string;
  fields: SimpleField[];
  initialValues: Record<string, string>;
  submitLabel: string;
  submitting?: boolean;
  /** Field errors returned by the server (ErrorResponse.fieldErrors), keyed by field name. */
  serverErrors?: Record<string, string>;
  /** Non-field error returned by the server, e.g. a duplicate name. */
  serverError?: string | null;
  onClose: () => void;
  onSubmit: (values: Record<string, string>) => void;
}

const inputClass =
  'w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0';
const labelClass = 'block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1';

/** Small form of required text fields, used for zones and departments. Values are sent trimmed. */
export const SimpleFormModal: React.FC<SimpleFormModalProps> = ({
  eyebrow,
  title,
  fields,
  initialValues,
  submitLabel,
  submitting = false,
  serverErrors = {},
  serverError = null,
  onClose,
  onSubmit,
}) => {
  const [values, setValues] = useState<Record<string, string>>(initialValues);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = Object.fromEntries(fields.map((f) => [f.name, (values[f.name] ?? '').trim()]));
    const found: Record<string, string> = {};
    fields.forEach((f) => {
      if (!trimmed[f.name]) found[f.name] = `${f.label} is required.`;
      else if (trimmed[f.name].length > f.maxLength) found[f.name] = `${f.label} must be at most ${f.maxLength} characters.`;
    });
    setErrors(found);
    if (Object.keys(found).length === 0) onSubmit(trimmed);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="w-full max-w-md bg-[#FAFAF8] rounded-[8px] shadow-hero overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        <div className="px-6 py-4 border-b border-[#E5E0D8]/60 flex items-center justify-between bg-[#F0EDE8]/60">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">{eyebrow}</span>
            <h2 className="text-base font-bold text-[#242424] font-editorial">{title}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-[4px] flex items-center justify-center text-[#8A8070] hover:text-[#242424] hover:bg-[#EAE5DC] transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate className="p-6 space-y-4">
          {serverError && (
            <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
              {serverError}
            </div>
          )}

          {fields.map((f) => {
            const error = errors[f.name] ?? serverErrors[f.name];
            return (
              <div key={f.name}>
                <label className={labelClass} htmlFor={`simple-form-${f.name}`}>
                  {f.label}
                </label>
                <input
                  id={`simple-form-${f.name}`}
                  type="text"
                  maxLength={f.maxLength}
                  placeholder={f.placeholder}
                  value={values[f.name] ?? ''}
                  onChange={(e) => setValues((prev) => ({ ...prev, [f.name]: e.target.value }))}
                  className={inputClass}
                />
                {error && <p className="mt-1 text-[11px] text-[#C62828] font-medium">{error}</p>}
              </div>
            );
          })}

          <div className="pt-3 border-t border-[#E5E0D8]/60 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-[6px] text-[#8A8070] hover:text-[#242424] hover:bg-[#F0EDE8] font-semibold text-xs transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2.5 rounded-[6px] bg-[#D97757] text-white font-semibold text-xs hover:opacity-95 shadow-card transition-all cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {submitting ? 'Saving…' : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

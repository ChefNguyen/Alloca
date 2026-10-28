import React, { useState } from 'react';
import { DepartmentSummary, Resource, ResourceType, Zone } from '@/types/api';

// Mirrors ResourceCreateRequest / ResourceUpdateRequest in docs/api/openapi.yaml
export interface ResourceFormValues {
  name: string;
  type: ResourceType;
  capacity: number;
  description: string | null;
  zoneId: string;
  departmentId: string | null;
  utilizationThreshold: number;
  initialStatus: 'AVAILABLE' | 'SUSPENDED';
}

interface ResourceFormModalProps {
  isOpen: boolean;
  resource: Resource | null; // null = create mode
  zones: Zone[];
  departments: DepartmentSummary[];
  submitting?: boolean;
  /** Field errors returned by the server (ErrorResponse.fieldErrors), keyed by field name. */
  serverErrors?: Record<string, string>;
  /** Non-field error returned by the server, e.g. a failed business rule. */
  serverError?: string | null;
  onClose: () => void;
  onSubmit: (values: ResourceFormValues) => void;
}

const RESOURCE_TYPES: { value: ResourceType; label: string }[] = [
  { value: 'ROOM', label: 'Room' },
  { value: 'EQUIPMENT', label: 'Equipment' },
  { value: 'DESK', label: 'Desk' },
];

const inputClass =
  'w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0';
const labelClass = 'block text-[11px] font-semibold text-[#8A8070] uppercase tracking-widest mb-1';

const toFormValues = (resource: Resource | null, zones: Zone[]): ResourceFormValues => ({
  name: resource?.name ?? '',
  type: resource?.type ?? 'ROOM',
  capacity: resource?.capacity ?? 1,
  description: resource?.description ?? null,
  zoneId: resource?.zone?.id ?? zones[0]?.id ?? '',
  departmentId: resource?.department?.id ?? null,
  utilizationThreshold: resource?.utilizationThreshold ?? 85,
  initialStatus: 'AVAILABLE',
});

const validate = (values: ResourceFormValues): Record<string, string> => {
  const errors: Record<string, string> = {};
  const name = values.name.trim();
  if (!name) errors.name = 'Name is required.';
  else if (name.length > 100) errors.name = 'Name must be at most 100 characters.';
  if (!Number.isInteger(values.capacity) || values.capacity < 1 || values.capacity > 10000) {
    errors.capacity = 'Capacity must be a whole number between 1 and 10000.';
  }
  if (!values.zoneId) errors.zoneId = 'Zone is required.';
  if ((values.description ?? '').length > 2000) {
    errors.description = 'Description must be at most 2000 characters.';
  }
  if (
    Number.isNaN(values.utilizationThreshold) ||
    values.utilizationThreshold < 0 ||
    values.utilizationThreshold > 100
  ) {
    errors.utilizationThreshold = 'Threshold must be between 0 and 100.';
  }
  return errors;
};

export const ResourceFormModal: React.FC<ResourceFormModalProps> = ({
  isOpen,
  resource,
  zones,
  departments,
  submitting = false,
  serverErrors = {},
  serverError = null,
  onClose,
  onSubmit,
}) => {
  const [values, setValues] = useState<ResourceFormValues>(() => toFormValues(resource, zones));
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (!isOpen) return null;

  const isEdit = resource !== null;

  const update = <K extends keyof ResourceFormValues>(key: K, value: ResourceFormValues[K]) => {
    setValues((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => {
      const { [key]: _removed, ...rest } = prev;
      return rest;
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const nextErrors = validate(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const description = values.description?.trim() || null;
    onSubmit({ ...values, name: values.name.trim(), description });
  };

  const shownErrors = { ...serverErrors, ...errors };
  const fieldError = (key: string) =>
    shownErrors[key] ? <p className="mt-1 text-[11px] text-[#C62828] font-medium">{shownErrors[key]}</p> : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs p-4">
      <div className="w-full max-w-lg bg-[#FAFAF8] rounded-[8px] shadow-hero overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        {/* Modal Header */}
        <div className="px-6 py-4 border-b border-[#E5E0D8]/60 flex items-center justify-between bg-[#F0EDE8]/60">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Resource Asset Catalog
            </span>
            <h2 className="text-base font-bold text-[#242424] font-editorial">
              {isEdit ? `Edit "${resource.name}"` : 'Create a New Resource'}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-8 h-8 rounded-[4px] flex items-center justify-center text-[#8A8070] hover:text-[#242424] hover:bg-[#EAE5DC] transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} noValidate className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
          {serverError && (
            <div
              role="alert"
              className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold"
            >
              {serverError}
            </div>
          )}

          <div>
            <label className={labelClass}>Name</label>
            <input
              type="text"
              maxLength={100}
              placeholder="e.g. Boardroom West 301"
              value={values.name}
              onChange={(e) => update('name', e.target.value)}
              className={inputClass}
            />
            {fieldError('name')}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Type</label>
              <select
                value={values.type}
                onChange={(e) => update('type', e.target.value as ResourceType)}
                className={inputClass}
              >
                {RESOURCE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={labelClass}>Capacity</label>
              <input
                type="number"
                min={1}
                max={10000}
                value={Number.isNaN(values.capacity) ? '' : values.capacity}
                onChange={(e) => update('capacity', e.target.valueAsNumber)}
                className={inputClass}
              />
              {fieldError('capacity')}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Zone</label>
              <select
                value={values.zoneId}
                onChange={(e) => update('zoneId', e.target.value)}
                className={inputClass}
              >
                {zones.map((z) => (
                  <option key={z.id} value={z.id}>
                    {z.building} · {z.floor} · {z.name}
                  </option>
                ))}
              </select>
              {fieldError('zoneId')}
            </div>
            <div>
              <label className={labelClass}>Owning Department</label>
              <select
                value={values.departmentId ?? ''}
                onChange={(e) => update('departmentId', e.target.value || null)}
                className={inputClass}
              >
                <option value="">Shared (company-wide)</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <p className="text-[11px] text-[#8A8070] -mt-2">
            Department only routes utilization reports and alerts — anyone can still book the resource.
          </p>

          <div className={`grid gap-3 ${isEdit ? 'grid-cols-1' : 'grid-cols-2'}`}>
            <div>
              <label className={labelClass}>Utilization Alert Threshold (%)</label>
              <input
                type="number"
                min={0}
                max={100}
                step={0.5}
                value={Number.isNaN(values.utilizationThreshold) ? '' : values.utilizationThreshold}
                onChange={(e) => update('utilizationThreshold', e.target.valueAsNumber)}
                className={inputClass}
              />
              {fieldError('utilizationThreshold')}
            </div>
            {!isEdit && (
              <div>
                <label className={labelClass}>Initial Status</label>
                <select
                  value={values.initialStatus}
                  onChange={(e) => update('initialStatus', e.target.value as 'AVAILABLE' | 'SUSPENDED')}
                  className={inputClass}
                >
                  <option value="AVAILABLE">Available</option>
                  <option value="SUSPENDED">Suspended</option>
                </select>
              </div>
            )}
          </div>

          <div>
            <label className={labelClass}>Description</label>
            <textarea
              rows={3}
              maxLength={2000}
              placeholder="Equipment, amenities, access notes..."
              value={values.description ?? ''}
              onChange={(e) => update('description', e.target.value)}
              className="w-full px-3.5 py-3 rounded-[6px] bg-[#F0EDE8]/60 text-[#242424] text-xs font-semibold focus:outline-none focus:ring-1 focus:ring-[#8A8070] border-0 resize-none"
            />
            {fieldError('description')}
          </div>

          {/* Modal Footer */}
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
              {submitting ? 'Saving…' : isEdit ? 'Save Changes' : 'Create Resource'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

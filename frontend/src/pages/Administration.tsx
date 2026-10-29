import React, { useCallback, useEffect, useState } from 'react';
import { ResourceFormModal, ResourceFormValues } from '@/components/admin/ResourceFormModal';
import { ResourceRowActions } from '@/components/admin/ResourceRowActions';
import { ResourceScheduleModal } from '@/components/admin/ResourceScheduleModal';
import { SimpleField, SimpleFormModal } from '@/components/admin/SimpleFormModal';
import { UserFormModal, UserFormValues } from '@/components/admin/UserFormModal';
import { ApiError } from '@/lib/api';
import {
  createDepartment,
  createResource,
  createZone,
  deleteDepartment,
  deleteResource,
  deleteZone,
  listDepartments,
  listResources,
  listZones,
  updateDepartment,
  updateResource,
  updateResourceStatus,
  updateZone,
} from '@/services/resourceApi';
import {
  createUser,
  deactivateUser,
  listUsers,
  updateUser,
  updateUserAccess,
} from '@/services/userApi';
import { Department, Resource, User, Zone } from '@/types/api';

// Mirror ZoneWriteRequest / DepartmentWriteRequest limits in docs/api/openapi.yaml
const ZONE_FIELDS: SimpleField[] = [
  { name: 'name', label: 'Zone name', maxLength: 100, placeholder: 'e.g. West Wing' },
  { name: 'floor', label: 'Floor', maxLength: 50, placeholder: 'e.g. Level 3' },
  { name: 'building', label: 'Building', maxLength: 100, placeholder: 'e.g. Tower A' },
];
const DEPARTMENT_FIELDS: SimpleField[] = [
  { name: 'name', label: 'Department name', maxLength: 100, placeholder: 'e.g. Engineering' },
];

// `target === null` means create mode.
type EntityForm = { kind: 'zone'; target: Zone | null } | { kind: 'department'; target: Department | null };

const describeError = (error: unknown): string => {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.';
  const bookingIds = error.details.conflictingBookingIds;
  if (error.status === 409 && Array.isArray(bookingIds)) {
    return `Cannot delete: ${bookingIds.length} ongoing or upcoming CONFIRMED booking(s) still use this resource (${bookingIds.join(', ')}). Cancel or move them first.`;
  }
  return error.message;
};

export const Administration: React.FC = () => {
  const [activeTab, setActiveTab] = useState<
    'resources' | 'zones' | 'users' | 'departments' | 'settings'
  >('resources');

  const [resources, setResources] = useState<Resource[]>([]);
  const [totalResources, setTotalResources] = useState(0);
  const [zones, setZones] = useState<Zone[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Real users state (ALC-26, ALC-27)
  const [users, setUsers] = useState<User[]>([]);
  const [totalUsers, setTotalUsers] = useState(0);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [editingUser, setEditingUser] = useState<User | null | undefined>(undefined);
  const [userSubmitting, setUserSubmitting] = useState(false);
  const [userFormError, setUserFormError] = useState<string | null>(null);
  const [userFormFieldErrors, setUserFormFieldErrors] = useState<Record<string, string>>({});

  const [reminderLeadTime, setReminderLeadTime] = useState<number>(15);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; message: string } | null>(null);

  // `editing === undefined` means the modal is closed; `null` means create mode.
  const [editing, setEditing] = useState<Resource | null | undefined>(undefined);
  const [schedulingResource, setSchedulingResource] = useState<Resource | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formFieldErrors, setFormFieldErrors] = useState<Record<string, string>>({});
  const [entityForm, setEntityForm] = useState<EntityForm | null>(null);

  const loadResources = useCallback(async () => {
    const page = await listResources({ size: 100, sort: 'name,asc' });
    setResources(page.items);
    setTotalResources(page.page.totalElements);
  }, []);

  const loadUsers = useCallback(async () => {
    try {
      setUsersLoading(true);
      const page = await listUsers({ size: 100, sort: 'createdAt,desc' });
      setUsers(page.items);
      setTotalUsers(page.page.totalElements);
      setUsersError(null);
    } catch (error) {
      setUsersError(describeError(error));
    } finally {
      setUsersLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.all([loadResources(), listZones(), listDepartments(), loadUsers()])
      .then(([, loadedZones, loadedDepartments]) => {
        if (cancelled) return;
        setZones(loadedZones);
        setDepartments(loadedDepartments);
        setLoadError(null);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(describeError(error));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [loadResources, loadUsers]);

  const closeForm = () => {
    setEditing(undefined);
    setFormError(null);
    setFormFieldErrors({});
  };

  const openForm = (resource: Resource | null) => {
    setFormError(null);
    setFormFieldErrors({});
    setEditing(resource);
  };

  const openEntityForm = (form: EntityForm) => {
    setFormError(null);
    setFormFieldErrors({});
    setEntityForm(form);
  };

  const closeEntityForm = () => {
    setEntityForm(null);
    setFormError(null);
    setFormFieldErrors({});
  };

  // Resources embed zone/department summaries, so they are reloaded after a rename too.
  const reloadZones = async () => {
    const [loadedZones] = await Promise.all([listZones(), loadResources()]);
    setZones(loadedZones);
  };

  const reloadDepartments = async () => {
    const [loadedDepartments] = await Promise.all([listDepartments(), loadResources()]);
    setDepartments(loadedDepartments);
  };

  const handleSubmitEntity = async (values: Record<string, string>) => {
    if (!entityForm) return;
    setSubmitting(true);
    setFormError(null);
    setFormFieldErrors({});
    try {
      if (entityForm.kind === 'zone') {
        const input = { name: values.name, floor: values.floor, building: values.building };
        if (entityForm.target) await updateZone(entityForm.target.id, input);
        else await createZone(input);
        await reloadZones();
        showNotice(entityForm.target ? `Zone "${values.name}" updated.` : `Zone "${values.name}" created.`);
      } else {
        if (entityForm.target) await updateDepartment(entityForm.target.id, values.name);
        else await createDepartment(values.name);
        await reloadDepartments();
        showNotice(
          entityForm.target ? `Department "${values.name}" updated.` : `Department "${values.name}" created.`
        );
      }
      closeEntityForm();
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        setFormFieldErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])));
      }
      setFormError(describeError(error));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteZone = async (zone: Zone) => {
    if (
      !window.confirm(
        `Delete zone "${zone.name}" (${zone.building} · ${zone.floor})? It will be hidden but kept for booking history.`
      )
    )
      return;
    try {
      await deleteZone(zone.id);
      await reloadZones();
      showNotice(`Zone "${zone.name}" deleted.`);
    } catch (error) {
      showNotice(describeError(error), 'error');
    }
  };

  const handleDeleteDepartment = async (department: Department) => {
    if (!window.confirm(`Delete department "${department.name}"?`)) return;
    try {
      await deleteDepartment(department.id);
      await reloadDepartments();
      showNotice(`Department "${department.name}" deleted.`);
    } catch (error) {
      showNotice(describeError(error), 'error');
    }
  };

  const handleSubmitResource = async (values: ResourceFormValues) => {
    setSubmitting(true);
    setFormError(null);
    setFormFieldErrors({});
    try {
      const shared = {
        name: values.name,
        type: values.type,
        capacity: values.capacity,
        description: values.description,
        zoneId: values.zoneId,
        departmentId: values.departmentId,
        utilizationThreshold: values.utilizationThreshold,
      };
      if (editing) {
        await updateResource(editing.id, shared);
      } else {
        await createResource({ ...shared, status: values.initialStatus });
      }
      await loadResources();
      showNotice(editing ? `Resource "${values.name}" updated.` : `Resource "${values.name}" created.`);
      closeForm();
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        setFormFieldErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])));
      }
      setFormError(describeError(error));
    } finally {
      setSubmitting(false);
    }
  };

  const toggleResourceStatus = async (resource: Resource) => {
    const next = resource.status === 'AVAILABLE' ? 'SUSPENDED' : 'AVAILABLE';
    try {
      await updateResourceStatus(resource.id, next);
      await loadResources();
      showNotice(`"${resource.name}" is now ${next}.`);
    } catch (error) {
      showNotice(describeError(error), 'error');
    }
  };

  const handleSoftDeleteResource = async (resource: Resource) => {
    if (resource.status !== 'SUSPENDED') {
      showNotice('Resource must be SUSPENDED before it can be deleted (BR-RESOURCE-01).', 'error');
      return;
    }
    if (!window.confirm(`Delete "${resource.name}"? It will be hidden from users but kept for history.`)) return;

    try {
      await deleteResource(resource.id);
      await loadResources();
      showNotice(`Resource "${resource.name}" has been soft-deleted (status = DELETED).`);
    } catch (error) {
      showNotice(describeError(error), 'error');
    }
  };

  const handleToggleUserActive = async (targetUser: User) => {
    try {
      if (targetUser.isActive) {
        if (
          !window.confirm(
            `Deactivate user "${targetUser.fullName}" (@${targetUser.username})? They will immediately lose access and credentials will be revoked.`,
          )
        ) {
          return;
        }
        await deactivateUser(targetUser.id);
        showNotice(`User "${targetUser.fullName}" has been deactivated.`);
      } else {
        await updateUser(targetUser.id, { isActive: true });
        showNotice(`User "${targetUser.fullName}" has been activated.`);
      }
      await loadUsers();
    } catch (error) {
      showNotice(describeError(error), 'error');
    }
  };

  const handleSubmitUser = async (values: UserFormValues) => {
    setUserSubmitting(true);
    setUserFormError(null);
    setUserFormFieldErrors({});
    try {
      if (!editingUser) {
        await createUser({
          username: values.username,
          email: values.email,
          password: values.password!,
          fullName: values.fullName,
          role: values.role,
          departmentId: values.departmentId,
        });
        showNotice(`User "${values.fullName}" created successfully.`);
      } else {
        await updateUser(editingUser.id, {
          email: values.email,
          fullName: values.fullName,
          newPassword: values.password ? values.password : undefined,
        });
        if (
          values.role !== editingUser.role ||
          values.departmentId !== editingUser.department?.id
        ) {
          await updateUserAccess(editingUser.id, {
            role: values.role,
            departmentId: values.departmentId,
          });
        }
        showNotice(`User "${values.fullName}" updated successfully.`);
      }
      setEditingUser(undefined);
      await loadUsers();
    } catch (error) {
      if (error instanceof ApiError) {
        setUserFormError(error.message);
        const fieldMap: Record<string, string> = {};
        error.fieldErrors.forEach((fe) => {
          fieldMap[fe.field] = fe.message;
        });
        setUserFormFieldErrors(fieldMap);
      } else {
        setUserFormError('Failed to save user. Please try again.');
      }
    } finally {
      setUserSubmitting(false);
    }
  };

  const showNotice = (message: string, kind: 'success' | 'error' = 'success') => {
    setFeedback({ kind, message });
    setTimeout(() => setFeedback(null), kind === 'error' ? 8000 : 3500);
  };

  return (
    <div className="w-full min-h-screen bg-[#F0EDE8] p-6 lg:p-8 space-y-6">
      {/* Top Editorial Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 pb-4 border-b border-[#E5E0D8]/60">
        <div>
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block mb-1">
            Governance & Enterprise Configuration
          </span>
          <h1 className="text-[48px] font-[300] text-[#242424] tracking-[0.06em] leading-none font-editorial">
            Administration
          </h1>
        </div>

        {/* Administration Menu Tabs */}
        <div className="inline-flex p-1 bg-[#FAFAF8] shadow-card rounded-[6px] gap-1 overflow-x-auto max-w-full">
          {[
            { key: 'resources', label: 'Resources & Schedules', icon: 'desk' },
            { key: 'zones', label: 'Zones & Floor Maps', icon: 'layers' },
            { key: 'users', label: 'Users & Roles', icon: 'group' },
            { key: 'departments', label: 'Departments', icon: 'corporate_fare' },
            { key: 'settings', label: 'System Settings', icon: 'tune' },
          ].map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key as any)}
              className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-[4px] text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                activeTab === tab.key
                  ? 'bg-[#242424] text-white shadow-xs'
                  : 'text-[#8A8070] hover:text-[#242424]'
              }`}
            >
              <span className="material-symbols-outlined text-[16px]">{tab.icon}</span>
              <span>{tab.label}</span>
            </button>
          ))}
        </div>
      </div>

      {feedback && (
        <div
          role={feedback.kind === 'error' ? 'alert' : 'status'}
          // Fixed so the notice is visible however far the page is scrolled.
          className="fixed top-4 right-4 z-[60] max-w-md p-4 rounded-[6px] bg-[#FAFAF8] shadow-xl border border-[#E5E0D8] text-[#242424] text-xs font-semibold flex items-center gap-2"
        >
          <span
            className={`material-symbols-outlined text-[18px] ${
              feedback.kind === 'error' ? 'text-[#E05252]' : 'text-[#22C55E]'
            }`}
          >
            {feedback.kind === 'error' ? 'error' : 'check_circle'}
          </span>
          <span>{feedback.message}</span>
        </div>
      )}

      {/* Tab 1: Resources & Schedules */}
      {activeTab === 'resources' && (
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
          <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
            <div>
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Resource Asset Catalog
              </span>
              <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                Workplace Assets & Policies
              </h2>
            </div>
            <button
              onClick={() => openForm(null)}
              disabled={zones.length === 0}
              title={zones.length === 0 ? 'Create a zone first — resources must belong to a zone' : undefined}
              className="px-4 py-2.5 rounded-[6px] bg-[#D97757] text-white text-xs font-bold shadow-card hover:opacity-95 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              + Add Resource
            </button>
          </div>

          {loadError && (
            <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
              Could not load data: {loadError}
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#E5E0D8]/60 bg-[#F0EDE8]/50 text-[#8A8070] uppercase font-bold text-[10px] tracking-wider">
                  <th className="py-3 px-3 rounded-l-[4px]">Name</th>
                  <th className="py-3 px-3">Type</th>
                  <th className="py-3 px-3">Zone</th>
                  <th className="py-3 px-3">Department</th>
                  <th className="py-3 px-3">Capacity</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3 text-right rounded-r-[4px]">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E5E0D8]/40">
                {!loading && !loadError && resources.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-8 px-3 text-center text-[#8A8070]">
                      No resources yet. Use “Add Resource” to create the first one.
                    </td>
                  </tr>
                )}
                {loading && (
                  <tr>
                    <td colSpan={7} className="py-8 px-3 text-center text-[#8A8070]">
                      Loading resources…
                    </td>
                  </tr>
                )}
                {resources.map((res) => (
                  <tr key={res.id} className="hover:bg-[#F0EDE8]/30 transition-colors">
                    <td className="py-3.5 px-3 font-bold text-[#242424]">{res.name}</td>
                    <td className="py-3.5 px-3">{res.type}</td>
                    <td className="py-3.5 px-3 text-[#8A8070] min-w-[9rem]">
                      {res.zone ? `${res.zone.building} · ${res.zone.floor} · ${res.zone.name}` : '—'}
                    </td>
                    <td className="py-3.5 px-3 text-[#8A8070]">{res.department?.name ?? 'Shared'}</td>
                    <td className="py-3.5 px-3">{res.capacity}</td>
                    <td className="py-3.5 px-3">
                      <span
                        className={`px-2.5 py-0.5 rounded-[4px] text-[10px] font-bold ${
                          res.status === 'AVAILABLE'
                            ? 'bg-[#22C55E]/10 text-[#22C55E]'
                            : 'bg-[#F59E0B]/15 text-[#B45309]'
                        }`}
                      >
                        {res.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-3 text-right">
                      <ResourceRowActions
                        resource={res}
                        onEdit={openForm}
                        onEditHours={setSchedulingResource}
                        onToggleStatus={toggleResourceStatus}
                        onDelete={handleSoftDeleteResource}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalResources > resources.length && (
            <p className="text-[11px] text-[#8A8070]">
              Showing the first {resources.length} of {totalResources} resources.
            </p>
          )}
        </div>
      )}

      {editing !== undefined && (
        <ResourceFormModal
          key={editing?.id ?? 'new'}
          isOpen
          resource={editing}
          zones={zones}
          departments={departments}
          submitting={submitting}
          serverError={formError}
          serverErrors={formFieldErrors}
          onClose={closeForm}
          onSubmit={handleSubmitResource}
        />
      )}

      {entityForm && (
        <SimpleFormModal
          key={`${entityForm.kind}-${entityForm.target?.id ?? 'new'}`}
          eyebrow={entityForm.kind === 'zone' ? 'Zones & Floor Maps' : 'Organizational Structure'}
          title={
            entityForm.target
              ? `Edit "${entityForm.target.name}"`
              : entityForm.kind === 'zone'
                ? 'Create a New Zone'
                : 'Create a New Department'
          }
          fields={entityForm.kind === 'zone' ? ZONE_FIELDS : DEPARTMENT_FIELDS}
          initialValues={
            entityForm.kind === 'zone'
              ? {
                  name: entityForm.target?.name ?? '',
                  floor: entityForm.target?.floor ?? '',
                  building: entityForm.target?.building ?? '',
                }
              : { name: entityForm.target?.name ?? '' }
          }
          submitLabel={entityForm.target ? 'Save Changes' : entityForm.kind === 'zone' ? 'Create Zone' : 'Create Department'}
          submitting={submitting}
          serverError={formError}
          serverErrors={formFieldErrors}
          onClose={closeEntityForm}
          onSubmit={handleSubmitEntity}
        />
      )}

      {schedulingResource && (
        <ResourceScheduleModal
          key={schedulingResource.id}
          resource={schedulingResource}
          onClose={() => setSchedulingResource(null)}
          onSaved={(saved) => {
            setSchedulingResource(null);
            showNotice(`Operating hours for "${saved.name}" saved.`);
          }}
        />
      )}

      {/* Tab 2: Zones & Floor Maps */}
      {activeTab === 'zones' && (
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
          <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
            <div>
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Zones & Floor Maps
              </span>
              <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                Floor Blueprints & Boundaries
              </h2>
            </div>
            <button
              onClick={() => openEntityForm({ kind: 'zone', target: null })}
              className="px-4 py-2.5 rounded-[6px] bg-[#D97757] text-white text-xs font-bold shadow-card hover:opacity-95 transition-all cursor-pointer"
            >
              + Add Zone
            </button>
          </div>

          {!loading && zones.length === 0 && (
            <p className="py-8 text-center text-xs text-[#8A8070]">
              No zones yet. Create a zone (building + floor) before adding resources to it.
            </p>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {zones.map((z) => (
              <div
                key={z.id}
                className="p-5 rounded-[8px] bg-[#F0EDE8]/40 space-y-3.5"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-sm text-[#242424]">{z.name}</span>
                  <span className="text-xs font-semibold px-2.5 py-0.5 rounded-[4px] bg-[#FAFAF8] shadow-card text-[#242424]">
                    {z.floor}
                  </span>
                </div>

                <div className="aspect-[16/9] rounded-[6px] overflow-hidden relative bg-[#F0EDE8]">
                  {z.floorMapUrl ? (
                    <>
                      <img src={z.floorMapUrl} alt={z.name} className="w-full h-full object-cover" />
                      <div className="absolute bottom-2 left-2 px-2.5 py-1 rounded-[4px] bg-[#242424]/80 text-white text-[10px] font-mono backdrop-blur-sm">
                        {z.floorMapWidth} × {z.floorMapHeight} px
                      </div>
                    </>
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-xs text-[#8A8070]">
                      No floor map uploaded
                    </div>
                  )}
                </div>

                <div className="flex items-center justify-between pt-1 text-xs">
                  <span className="text-[#8A8070]">Building: {z.building}</span>
                  <button className="text-[#242424] font-semibold hover:underline cursor-pointer">
                    Upload new blueprint →
                  </button>
                </div>
                <div className="flex items-center justify-end gap-4 pt-2.5 border-t border-[#E5E0D8]/60 text-xs">
                  <button
                    onClick={() => openEntityForm({ kind: 'zone', target: z })}
                    className="text-[#242424] font-semibold hover:underline cursor-pointer"
                  >
                    Edit zone
                  </button>
                  <button
                    onClick={() => handleDeleteZone(z)}
                    className="text-[#E05252] font-semibold hover:underline cursor-pointer"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 3: Users & Roles */}
      {activeTab === 'users' && (
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
          <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
            <div>
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Directory & Access Control
              </span>
              <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                User Management & RBAC Roles
              </h2>
            </div>
            <button
              onClick={() => {
                setUserFormError(null);
                setUserFormFieldErrors({});
                setEditingUser(null);
              }}
              className="px-4 py-2.5 rounded-[6px] bg-[#D97757] text-white text-xs font-bold shadow-card hover:opacity-95 transition-all cursor-pointer"
            >
              + Add User
            </button>
          </div>

          {usersError && (
            <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
              Could not load users: {usersError}
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#E5E0D8]/60 bg-[#F0EDE8]/50 text-[#8A8070] uppercase font-bold text-[10px] tracking-wider">
                  <th className="py-3 px-4 rounded-l-[4px]">Full Name</th>
                  <th className="py-3 px-4">Username / Email</th>
                  <th className="py-3 px-4">Department</th>
                  <th className="py-3 px-4">Role</th>
                  <th className="py-3 px-4">Account Status</th>
                  <th className="py-3 px-4 text-right rounded-r-[4px]">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E5E0D8]/40">
                {usersLoading && (
                  <tr>
                    <td colSpan={6} className="py-8 px-4 text-center text-[#8A8070]">
                      Loading users from database...
                    </td>
                  </tr>
                )}
                {!usersLoading && users.length === 0 && (
                  <tr>
                    <td colSpan={6} className="py-8 px-4 text-center text-[#8A8070]">
                      No users found. Use “+ Add User” to create the first user.
                    </td>
                  </tr>
                )}
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-[#F0EDE8]/30 transition-colors">
                    <td className="py-3.5 px-4 font-bold text-[#242424]">{u.fullName}</td>
                    <td className="py-3.5 px-4 text-[#8A8070]">
                      <div>@{u.username}</div>
                      <div className="text-[11px]">{u.email}</div>
                    </td>
                    <td className="py-3.5 px-4 font-medium text-[#242424]">{u.department?.name || '—'}</td>
                    <td className="py-3.5 px-4">
                      <span className="font-bold text-[#242424] px-2.5 py-0.5 rounded-[4px] bg-[#F0EDE8]">
                        {u.role}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`px-2.5 py-0.5 rounded-[4px] text-[10px] font-bold ${
                          u.isActive
                            ? 'bg-[#22C55E]/10 text-[#22C55E]'
                            : 'bg-[#F0EDE8] text-[#8A8070]'
                        }`}
                      >
                        {u.isActive ? 'Active' : 'Deactivated'}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-right space-x-2">
                      <button
                        onClick={() => {
                          setUserFormError(null);
                          setUserFormFieldErrors({});
                          setEditingUser(u);
                        }}
                        className="px-2.5 py-1.5 rounded-[6px] text-xs font-semibold bg-[#F0EDE8] text-[#242424] hover:bg-[#E5E0D8] transition-colors cursor-pointer"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleToggleUserActive(u)}
                        className={`px-3 py-1.5 rounded-[6px] text-xs font-semibold transition-colors cursor-pointer ${
                          u.isActive
                            ? 'bg-[#242424] text-white hover:bg-black'
                            : 'bg-[#4CAF7D] text-white hover:bg-[#3D8B64]'
                        }`}
                      >
                        {u.isActive ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalUsers > users.length && (
            <p className="text-[11px] text-[#8A8070]">
              Showing the first {users.length} of {totalUsers} users.
            </p>
          )}
        </div>
      )}

      {editingUser !== undefined && (
        <UserFormModal
          isOpen
          user={editingUser}
          departments={departments}
          submitting={userSubmitting}
          serverError={userFormError}
          serverErrors={userFormFieldErrors}
          onClose={() => setEditingUser(undefined)}
          onSubmit={handleSubmitUser}
        />
      )}

      {/* Tab 4: Departments */}
      {activeTab === 'departments' && (
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-4 border-0">
          <div className="flex items-center justify-between pb-3 border-b border-[#E5E0D8]/60">
            <div>
              <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
                Organizational Structure
              </span>
              <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
                Departments & Allocation Budgets
              </h2>
            </div>
            <button
              onClick={() => openEntityForm({ kind: 'department', target: null })}
              className="px-4 py-2.5 rounded-[6px] bg-[#D97757] text-white text-xs font-bold shadow-card hover:opacity-95 transition-all cursor-pointer"
            >
              + Add Department
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {departments.map((dept) => (
              <div
                key={dept.id}
                className="p-5 rounded-[8px] bg-[#F0EDE8]/40 space-y-2.5"
              >
                <div className="flex items-center justify-between">
                  <span className="font-bold text-sm text-[#242424]">{dept.name}</span>
                  <span className="text-[11px] text-[#8A8070] font-mono">{dept.id}</span>
                </div>
                <p className="text-xs text-[#8A8070]">
                  Created: {new Date(dept.createdAt).toLocaleDateString()}
                </p>
                <div className="pt-2.5 border-t border-[#E5E0D8]/60 flex items-center justify-between text-xs">
                  <span className="font-medium text-[#22C55E]">● Active department</span>
                  <div className="flex items-center gap-4">
                    <button
                      onClick={() => openEntityForm({ kind: 'department', target: dept })}
                      className="text-[#242424] font-semibold hover:underline cursor-pointer"
                    >
                      Edit department →
                    </button>
                    <button
                      onClick={() => handleDeleteDepartment(dept)}
                      className="text-[#E05252] font-semibold hover:underline cursor-pointer"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab 5: System Settings */}
      {activeTab === 'settings' && (
        <div className="bg-[#FAFAF8] rounded-[8px] p-6 shadow-card space-y-6 max-w-2xl border-0">
          <div>
            <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
              Engine Parameters
            </span>
            <h2 className="text-lg font-light text-[#242424] font-editorial mt-0.5">
              System Settings & Background Automations
            </h2>
          </div>

          <div className="space-y-4">
            {/* Reminder Lead Time */}
            <div className="p-4 rounded-[6px] bg-[#F0EDE8]/40 space-y-2">
              <label className="block text-xs font-bold text-[#242424]">
                Booking Reminder Lead Time (Minutes)
              </label>
              <p className="text-xs text-[#8A8070]">
                System rule BR-JOB-01: Admin configurable lead time (1–1440 minutes). Default is 15 minutes.
              </p>
              <div className="flex items-center gap-3 pt-1">
                <input
                  type="number"
                  min="1"
                  max="1440"
                  value={reminderLeadTime}
                  onChange={(e) => setReminderLeadTime(Number(e.target.value))}
                  className="w-28 h-10 px-3 rounded-[6px] bg-[#FAFAF8] shadow-card text-sm font-bold text-[#242424] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
                />
                <span className="text-xs text-[#8A8070] font-medium">
                  Minutes before start_time (Current: {reminderLeadTime} min)
                </span>
              </div>
            </div>

            {/* Threshold */}
            <div className="p-4 rounded-[6px] bg-[#F0EDE8]/40 space-y-2">
              <label className="block text-xs font-bold text-[#242424]">
                Default Space Utilization Alert Threshold (%)
              </label>
              <p className="text-xs text-[#8A8070]">
                Evaluated daily across rolling 7-day windows. Resources exceeding this rate trigger alerts.
              </p>
              <input
                type="number"
                disabled
                value="85"
                className="w-28 h-10 px-3 rounded-[6px] bg-[#F0EDE8] text-sm font-bold text-[#8A8070] border-0"
              />
              <span className="text-xs text-[#8A8070] font-medium ml-3">
                Locked baseline threshold: 85.0%
              </span>
            </div>

            <button
              onClick={() => showNotice('System settings saved successfully.')}
              className="px-5 py-2.5 rounded-[6px] bg-[#242424] hover:bg-black text-white text-xs font-bold shadow-card transition-colors cursor-pointer"
            >
              Save Settings
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

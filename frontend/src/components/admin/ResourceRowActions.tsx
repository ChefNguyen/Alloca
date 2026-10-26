import React from 'react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { Resource } from '@/types/api';

interface ResourceRowActionsProps {
  resource: Resource;
  onEdit: (resource: Resource) => void;
  onEditHours: (resource: Resource) => void;
  onToggleStatus: (resource: Resource) => void;
  onDelete: (resource: Resource) => void;
}

const itemClass =
  'flex items-center gap-2.5 px-3 py-2 rounded-[4px] text-xs font-semibold text-[#242424] cursor-pointer outline-none ' +
  'data-[highlighted]:bg-[#F0EDE8] data-[disabled]:text-[#A8A29E] data-[disabled]:cursor-not-allowed data-[disabled]:data-[highlighted]:bg-transparent';

const iconClass = 'material-symbols-outlined text-[16px]';

/** Row actions behind a three-dot menu; the menu is portalled so the table's scroll area never clips it. */
export const ResourceRowActions: React.FC<ResourceRowActionsProps> = ({
  resource,
  onEdit,
  onEditHours,
  onToggleStatus,
  onDelete,
}) => {
  const canDelete = resource.status === 'SUSPENDED';
  const isAvailable = resource.status === 'AVAILABLE';

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button
          type="button"
          aria-label={`Actions for ${resource.name}`}
          className="w-8 h-8 rounded-[6px] flex items-center justify-center text-[#8A8070] hover:text-[#242424] hover:bg-[#F0EDE8] data-[state=open]:bg-[#F0EDE8] data-[state=open]:text-[#242424] transition-colors cursor-pointer"
        >
          <span className="material-symbols-outlined text-[20px]">more_vert</span>
        </button>
      </DropdownMenu.Trigger>

      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={4}
          className="z-50 min-w-[190px] p-1.5 bg-[#FAFAF8] border border-[#E5E0D8] rounded-[8px] shadow-xl"
        >
          <DropdownMenu.Item className={itemClass} onSelect={() => onEdit(resource)}>
            <span className={iconClass}>edit</span>
            Edit details
          </DropdownMenu.Item>
          <DropdownMenu.Item className={itemClass} onSelect={() => onEditHours(resource)}>
            <span className={iconClass}>schedule</span>
            Operating hours
          </DropdownMenu.Item>
          <DropdownMenu.Item className={itemClass} onSelect={() => onToggleStatus(resource)}>
            <span className={iconClass}>{isAvailable ? 'pause_circle' : 'play_circle'}</span>
            {isAvailable ? 'Suspend' : 'Activate'}
          </DropdownMenu.Item>

          <DropdownMenu.Separator className="my-1.5 h-px bg-[#E5E0D8]/70" />

          <DropdownMenu.Item
            disabled={!canDelete}
            className={`${itemClass} ${canDelete ? 'text-[#C62828] data-[highlighted]:bg-[#FFEBEE]' : ''}`}
            onSelect={() => onDelete(resource)}
          >
            <span className={iconClass}>delete</span>
            <span className="flex flex-col">
              <span>Delete</span>
              {!canDelete && <span className="text-[10px] font-medium">Suspend it first</span>}
            </span>
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
};

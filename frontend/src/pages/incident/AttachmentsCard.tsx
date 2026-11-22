import { Card, FileButton, Group, Image, Loader, Modal, SimpleGrid, Text, Tooltip, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCamera, IconPhotoPlus } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { AttachmentView, IncidentDetail } from '../../api/types';
import { errorMessage, formatDateTime } from '../../lib/format';

/** Loads one photo through the authenticated API and frees the object URL afterwards. */
function useObjectUrl(attachmentId: number) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let current: string | null = null;
    let cancelled = false;
    api.attachments
      .objectUrl(attachmentId)
      .then((u) => {
        current = u;
        if (!cancelled) setUrl(u);
        else URL.revokeObjectURL(u);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
      if (current) URL.revokeObjectURL(current);
    };
  }, [attachmentId]);
  return url;
}

function Thumbnail({ attachment, onOpen }: { attachment: AttachmentView; onOpen: (url: string) => void }) {
  const url = useObjectUrl(attachment.id);
  return (
    <Tooltip label={`${attachment.uploadedBy.fullName} · ${formatDateTime(attachment.createdAt)}`}>
      <UnstyledButton onClick={() => url && onOpen(url)} aria-label={`Open ${attachment.fileName}`}>
        {url ? <Image src={url} h={88} radius="sm" fit="cover" alt={attachment.fileName} /> : <Loader size="sm" m="md" />}
      </UnstyledButton>
    </Tooltip>
  );
}

/** Photos of the problem (often taken with the phone app's camera); staff and the reporter can add more. */
export function AttachmentsCard({ incident }: { incident: IncidentDetail }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['incidents', incident.id, 'attachments'], queryFn: () => api.attachments.list(incident.id) });
  const upload = useMutation({
    mutationFn: (file: File) => api.attachments.upload(incident.id, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['incidents', incident.id, 'attachments'] });
      notifications.show({ color: 'green', message: 'Photo attached' });
    },
    onError: (e) => notifications.show({ color: 'red', title: 'Upload failed', message: errorMessage(e) }),
  });
  const closed = incident.status === 'CLOSED' || incident.status === 'CANCELLED';
  const items = list.data ?? [];
  if (closed && items.length === 0) return null;

  return (
    <Card withBorder radius="md">
      <Group justify="space-between" mb="xs">
        <Group gap="xs">
          <IconCamera size={18} />
          <Text fw={600}>Photos ({items.length})</Text>
        </Group>
        {!closed && (
          <FileButton onChange={(f) => f && upload.mutate(f)} accept="image/png,image/jpeg,image/webp,image/heic">
            {(props) => (
              <UnstyledButton {...props} aria-label="Attach a photo">
                <Group gap={4} c="var(--mantine-primary-color-filled)">
                  {upload.isPending ? <Loader size={14} /> : <IconPhotoPlus size={16} />}
                  <Text size="sm">Add</Text>
                </Group>
              </UnstyledButton>
            )}
          </FileButton>
        )}
      </Group>
      {items.length === 0 ? (
        <Text size="sm" c="dimmed">
          No photos yet. Screenshots of the error help support resolve it faster.
        </Text>
      ) : (
        <SimpleGrid cols={3} spacing="xs">
          {items.map((a) => (
            <Thumbnail key={a.id} attachment={a} onOpen={setOpen} />
          ))}
        </SimpleGrid>
      )}
      <Modal opened={open !== null} onClose={() => setOpen(null)} size="xl" centered withCloseButton title="Photo">
        {open && <Image src={open} fit="contain" mah="75vh" alt="Attachment" />}
      </Modal>
    </Card>
  );
}

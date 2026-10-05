// The page for unknown paths. The server answers them with its prerendered copy and a 404 status.

import { Title } from '@solidjs/meta';
import { SearchXIcon } from '../../components/icon';
import { buttonClass, State } from '../../components/ui';

export default function NotFound() {
  return (
    <State icon={<SearchXIcon />} title="Page not found">
      <Title>Page not found · NoHorny</Title>
      <p class="text-ink-2">There is nothing at this address.</p>
      <a class={buttonClass('primary')} href="/">
        Go to the home page
      </a>
    </State>
  );
}

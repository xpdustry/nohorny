import { Loading } from 'solid-js';
import { ConfirmHost } from './components/dialog';
import { OverlayScrollbar } from './components/scrollbar';
import { TooltipHost } from './components/tooltip';
import { Spinner } from './components/ui';
import { Router } from './router';
import './App.css';

export default function App() {
  return (
    <Router>
      {(props) => (
        <>
          <Loading
            fallback={
              <div class="grid min-h-dvh place-items-center text-ink-3">
                <Spinner class="size-6" />
              </div>
            }>
            {props.children}
          </Loading>
          <ConfirmHost />
          <OverlayScrollbar />
          <TooltipHost />
        </>
      )}
    </Router>
  );
}

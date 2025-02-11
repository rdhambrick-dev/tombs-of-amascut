package com.duckblade.osrs.toa.features.targettime;

import com.duckblade.osrs.toa.TombsOfAmascutConfig;
import com.duckblade.osrs.toa.module.PluginLifecycleComponent;
import com.duckblade.osrs.toa.util.RaidState;
import com.duckblade.osrs.toa.util.RaidStateChanged;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class TargetTimeManager implements PluginLifecycleComponent
{

	private static final int SCRIPT_TOA_TIME_UPDATE_TIMER = 6581;
	private static final int WIDGET_TIMER_PARENT_ID = 481;
	private static final int WIDGET_TIMER_CHILD_ID = 46;

	private static final String NO_TARGET_TIME_PREFIX = "You enter the Tombs of Amascut";
	private static final String TARGET_TIME_PREFIX = "Overall time to beat:";
	private static final Pattern TARGET_TIME_PATTERN = Pattern.compile(TARGET_TIME_PREFIX + " (\\d\\d:\\d\\d\\.\\d\\d)");

	private final EventBus eventBus;
	private final Client client;

	private String targetTime;

	@Override
	public boolean isEnabled(TombsOfAmascutConfig config, RaidState currentState)
	{
		return config.targetTimeDisplay();
	}

	@Override
	public void startUp()
	{
		eventBus.register(this);

		// scan for target time in history
		ChatLineBuffer gameMessages = client.getChatLineMap().get(ChatMessageType.GAMEMESSAGE.getType());
		if (gameMessages != null)
		{
			for (int i = 0; i < gameMessages.getLength(); i++)
			{
				MessageNode line = gameMessages.getLines()[i];
				if (NO_TARGET_TIME_PREFIX.equals(Text.removeTags(line.getValue())))
				{
					return;
				}

				if (checkMessage(Text.removeTags(line.getValue())))
				{
					return;
				}
			}
		}
	}

	@Override
	public void shutDown()
	{
		eventBus.unregister(this);
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		checkMessage(Text.removeTags(e.getMessage()));
	}

	@Subscribe
	public void onRaidStateChanged(RaidStateChanged e)
	{
		if (!e.getNewState().isInRaid())
		{
			targetTime = null;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() == SCRIPT_TOA_TIME_UPDATE_TIMER && targetTime != null)
		{
			Widget timer = client.getWidget(WIDGET_TIMER_PARENT_ID, WIDGET_TIMER_CHILD_ID);
			if (timer == null || timer.getText().contains("/"))
			{
				return;
			}

			timer.setText(timer.getText() + " / " + targetTime);

			// resize two fairly high up parent containers to prevent clipping
			Widget resize1 = timer.getParent().getParent().getParent();
			Widget resize2 = resize1.getParent();
			resize1.setSize(200, resize1.getHeight());
			resize2.setSize(200, resize2.getHeight());

			// propagate resizes backwards from parents since it uses negative width mode
			// parents need to have updated their width for children to derive correct width
			timer.getParent().getParent().getParent().getParent().revalidate(); // 3
			timer.getParent().getParent().getParent().revalidate(); // 5
			timer.getParent().getParent().revalidate(); // 39
			timer.getParent().revalidate(); // 40
			timer.revalidate(); // 46
		}
	}

	private boolean checkMessage(String msg)
	{
		if (!msg.startsWith(TARGET_TIME_PREFIX))
		{
			return false;
		}

		Matcher m = TARGET_TIME_PATTERN.matcher(msg);
		if (!m.find())
		{
			return false;
		}

		targetTime = m.group(1);
		return true;
	}
}

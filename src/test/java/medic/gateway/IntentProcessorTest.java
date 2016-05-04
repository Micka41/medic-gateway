package medic.gateway;

import android.content.*;
import android.provider.*;
import android.telephony.*;

import java.util.*;

import medic.gateway.test.*;

import org.junit.*;
import org.junit.runner.*;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.shadows.ShadowContentResolver.DeleteStatement;

import static android.provider.Telephony.Sms.Intents.*;
import static android.provider.Telephony.Sms.Inbox.CONTENT_URI;
import static medic.gateway.WoMessage.Status.*;
import static medic.gateway.test.DbTestHelper.*;
import static medic.gateway.test.TestUtils.*;
import static medic.gateway.test.UnitTestUtils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants=BuildConfig.class)
@SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
public class IntentProcessorTest {
	private IntentProcessor intentProcessor;

	private DbTestHelper db;
	private Capabilities mockCapabilities;
	private ShadowContentResolver contentResolver;

	@Before
	public void setUp() throws Exception {
		db = new DbTestHelper(RuntimeEnvironment.application);

		contentResolver = shadowOf(RuntimeEnvironment.application.getContentResolver());

		intentProcessor = new IntentProcessor();
		mockCapabilities = mockCapabilities(intentProcessor);
	}

	@After
	public void tearDown() throws Exception {
		db.tearDown();
	}

//> INCOMING SMS TESTS
	@Test
	public void test_onReceive_preKitkat_shouldNotIgnore_SMS_RECEIVED_ACTION() {
		// given
		preKitkat();

		// when
		aSmsReceivedActionArrives();

		// then
		db.assertCount("wt_message", 1);
	}

	@Test
	public void test_onReceive_kitkatPlus_shouldIgnore_SMS_RECEIVED_ACTION_ifDefaultSmsApp() {
		// given
		kitkatPlus();
		isDefaultSmsApp();

		// when
		aSmsReceivedActionArrives();

		// then
		db.assertCount("wt_message", 0);
	}

	@Test
	public void test_onReceive_kitkatPlus_shouldNotIgnore_SMS_RECEIVED_ACTION_ifNotDefaultSmsApp() {
		// given
		kitkatPlus();
		isNotDefaultSmsApp();

		// when
		aSmsReceivedActionArrives();

		// then
		db.assertCount("wt_message", 1);
	}

	@Test
	public void test_onReceive_kitkatPlus_shouldNotIgnore_SMS_DELIVERED_ACTION() {
		// given
		kitkatPlus();

		// when
		aSmsDeliveredActionArrives();

		// then
		db.assertCount("wt_message", 1);
	}

	@Test
	public void test_onReceive_shouldDeleteMessageFromDeviceInbox() {
		// given
		preKitkat();

		// when
		aSmsReceivedActionArrives();

		// then
		List<DeleteStatement> deletes = contentResolver.getDeleteStatements();
		assertEquals(1, deletes.size());
		assertEquals(CONTENT_URI, deletes.get(0).getUri());
		assertEquals("address=? AND date=? AND body=?", deletes.get(0).getWhere());
		assertArrayEquals(args("+447890123456", "1234360056000", "Good for you. Slap on the back etc."), deletes.get(0).getSelectionArgs());
	}

//> HELPERS
	private void preKitkat() { when(mockCapabilities.canBeDefaultSmsProvider()).thenReturn(false); }
	private void kitkatPlus() { when(mockCapabilities.canBeDefaultSmsProvider()).thenReturn(true); }

	private void isNotDefaultSmsApp() {
		when(mockCapabilities.isDefaultSmsProvider(RuntimeEnvironment.application))
				.thenReturn(false);
	}
	private void isDefaultSmsApp() {
		when(mockCapabilities.isDefaultSmsProvider(RuntimeEnvironment.application))
				.thenReturn(true);
	}

	private void aSmsDeliveredActionArrives() {
		deliver(smsIntent(SMS_DELIVER_ACTION));
	}

	private void aSmsReceivedActionArrives() {
		deliver(smsIntent(SMS_RECEIVED_ACTION));
	}

	private Intent smsIntent(String action) {
		Intent i = new Intent(action);
		i.putExtra("pdus", new Object[]{ A_VALID_GSM_PDU });
		i.putExtra("format", "3gpp");
		return i;
	}

	private void deliver(Intent i) {
		intentProcessor.onReceive(RuntimeEnvironment.application, i);
	}
}
